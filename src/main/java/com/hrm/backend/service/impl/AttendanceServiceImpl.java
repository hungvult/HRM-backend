package com.hrm.backend.service.impl;

import com.hrm.backend.config.AttendanceProperties;
import com.hrm.backend.dto.request.AttendanceLocationRequest;
import com.hrm.backend.dto.response.AttendanceActionResponse;
import com.hrm.backend.dto.response.AttendanceListItemResponse;
import com.hrm.backend.dto.response.MonthlyAttendanceResponse;
import com.hrm.backend.dto.response.TodayAttendanceResponse;
import com.hrm.backend.entity.AttendanceRecord;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.WorkLocation;
import com.hrm.backend.entity.WorkLocationWifiNetwork;
import com.hrm.backend.entity.enums.AttendanceStatus;
import com.hrm.backend.entity.enums.EmploymentStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.repository.AttendanceRecordRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.WorkLocationRepository;
import com.hrm.backend.service.AttendanceService;
import com.hrm.backend.service.CompanyWifiVerifier;
import com.hrm.backend.service.VerifiedCompanyWifi;
import com.hrm.backend.service.VerifiedWorkLocation;
import com.hrm.backend.service.WorkLocationVerifier;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final EmployeeRepository employees;
    private final AttendanceRecordRepository attendanceRecords;
    private final CompanyWifiVerifier wifiVerifier;
    private final WorkLocationVerifier workLocationVerifier;
    private final AttendanceProperties properties;
    private final Clock attendanceClock;
    private final WorkLocationRepository workLocations;

    @Override
    @Transactional
    // accountId là ID accounts từ principal đã xác thực; location là GPS app gửi, không chứa giờ công hoặc địa điểm do client chọn.
    // Khóa nhân viên đang làm việc, chọn địa điểm phía server rồi xác minh GPS; lỗi thì dừng trước khi lưu công.
    // Ngày công lấy theo attendanceClock và múi giờ công ty. Khóa attendance_records theo employee_id/work_date để chặn chấm trùng.
    // Sau kiểm tra trạng thái mới ghi check_in_at, địa điểm và GPS; Wi-Fi chỉ được ghi nếu thực sự đã xác minh.
    public AttendanceActionResponse checkIn(Long accountId, AttendanceLocationRequest location, HttpServletRequest request) {
        Employee employee = requireWorkingEmployee(accountId);
        AttendanceEvidence evidence = verifyAttendanceEvidence(location, request);
        VerifiedWorkLocation verifiedLocation = evidence.gps();
        Instant now = attendanceClock.instant();
        ZoneId zone = properties.companyZoneId();
        LocalDate workDate = now.atZone(zone).toLocalDate();

        AttendanceRecord record = attendanceRecords.findByEmployeeIdAndWorkDateForUpdate(employee.getId(), workDate)
                .orElseGet(() -> AttendanceRecord.builder()
                        .employee(employee)
                        .workDate(workDate)
                        .attendanceStatus(AttendanceStatus.PRESENT)
                        .isManuallyAdjusted(false)
                        .build());

        if (record.getCheckInAt() != null) {
            throw conflict("ATTENDANCE_ALREADY_CHECKED_IN", "Bạn đã check-in trước đó.");
        }

        record.setCheckInAt(OffsetDateTime.ofInstant(now, zone));
        // Chế độ GPS-only để trống bằng chứng Wi-Fi vì backend không xác minh mạng trong lượt này.
        record.setCheckInWifiSsid(evidence.wifi() == null ? null : evidence.wifi().getSsid());
        record.setCheckInWorkLocation(evidence.workLocation());
        record.setCheckInWifiNetwork(evidence.wifi());
        record.setCheckInLatitude(verifiedLocation.latitude());
        record.setCheckInLongitude(verifiedLocation.longitude());
        record.setCheckInGpsAccuracyMeters(location.getAccuracyMeters());
        record.setCheckInLocationCapturedAt(OffsetDateTime.ofInstant(location.getLocationCapturedAt(), zone));
        record.setCheckInLocationDistanceMeters(verifiedLocation.distanceMeters());
        AttendanceRecord saved = attendanceRecords.save(record);
        return response(saved, "Check-in thành công.");
    }

    @Override
    @Transactional
    // accountId là ID accounts từ principal; location là GPS mới do app lấy khi người dùng bấm check-out.
    // Xác minh nhân viên và vị trí trước, rồi khóa attendance_records theo nhân viên/ngày công từ giờ server và múi giờ công ty.
    // Chưa check-in hoặc đã check-out thì trả 409; chỉ khi hợp lệ mới ghi check_out_at, bằng chứng vị trí và working_minutes.
    // Giữ bằng chứng check-in cũ, kể cả khi hai lượt chạy ở hai chế độ Wi-Fi khác nhau; lỗi sẽ rollback toàn bộ lượt cập nhật.
    public AttendanceActionResponse checkOut(Long accountId, AttendanceLocationRequest location, HttpServletRequest request) {
        Employee employee = requireWorkingEmployee(accountId);
        AttendanceEvidence evidence = verifyAttendanceEvidence(location, request);
        VerifiedWorkLocation verifiedLocation = evidence.gps();
        Instant now = attendanceClock.instant();
        ZoneId zone = properties.companyZoneId();
        LocalDate workDate = now.atZone(zone).toLocalDate();
        AttendanceRecord record = attendanceRecords.findByEmployeeIdAndWorkDateForUpdate(employee.getId(), workDate)
                .orElseThrow(() -> conflict("ATTENDANCE_CHECK_IN_REQUIRED", "Bạn chưa check-in, không thể check-out."));

        if (record.getCheckInAt() == null) {
            throw conflict("ATTENDANCE_CHECK_IN_REQUIRED", "Bạn chưa check-in, không thể check-out.");
        }
        if (record.getCheckOutAt() != null) {
            throw conflict("ATTENDANCE_ALREADY_CHECKED_OUT", "Bạn đã check-out trước đó.");
        }

        long minutes = Duration.between(record.getCheckInAt().toInstant(), now).toMinutes();
        if (minutes < 0 || minutes > Integer.MAX_VALUE) {
            throw new IllegalStateException("Thời gian check-in/check-out không hợp lệ.");
        }
        record.setCheckOutAt(OffsetDateTime.ofInstant(now, zone));
        // Lưu địa điểm/GPS thực sự đã xác minh; không tự gán SSID hoặc network ID khi bỏ qua Wi-Fi.
        record.setCheckOutWifiSsid(evidence.wifi() == null ? null : evidence.wifi().getSsid());
        record.setCheckOutWorkLocation(evidence.workLocation());
        record.setCheckOutWifiNetwork(evidence.wifi());
        record.setCheckOutLatitude(verifiedLocation.latitude());
        record.setCheckOutLongitude(verifiedLocation.longitude());
        record.setCheckOutGpsAccuracyMeters(location.getAccuracyMeters());
        record.setCheckOutLocationCapturedAt(OffsetDateTime.ofInstant(location.getLocationCapturedAt(), zone));
        record.setCheckOutLocationDistanceMeters(verifiedLocation.distanceMeters());
        record.setWorkingMinutes((int) minutes);
        AttendanceRecord saved = attendanceRecords.save(record);
        return response(saved, "Check-out thành công.");
    }

    // Chọn địa điểm phía server trước khi kiểm tra GPS. ID cấu hình là ID work_locations, không phải Wi-Fi network ID.
    // Wi-Fi bật thì dùng luồng xác minh IP hiện có. Tạm tắt thì lấy địa điểm cố định, đang hoạt động từ DB.
    // Hàm chỉ đọc và xác minh; caller lưu bằng chứng sau khi kiểm tra trạng thái công thành công.
    private AttendanceEvidence verifyAttendanceEvidence(AttendanceLocationRequest location, HttpServletRequest request) {
        WorkLocationWifiNetwork network = null;
        WorkLocation workLocation;
        if (properties.isWifiVerificationEnabled()) {
            VerifiedCompanyWifi wifi = wifiVerifier.verify(request);
            network = wifi.network();
            workLocation = network.getWorkLocation();
        } else {
            workLocation = workLocations.findById(properties.getGpsWorkLocationId())
                    .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                    .orElseThrow(() -> new IllegalStateException("Địa điểm xác minh GPS chưa được cấu hình hoặc đã vô hiệu hóa."));
        }
        // Luôn dùng cùng bộ kiểm tra tọa độ, khoảng cách, độ chính xác và thời điểm GPS cho cả hai chế độ.
        VerifiedWorkLocation gps = workLocationVerifier.verify(location, workLocation);
        return new AttendanceEvidence(workLocation, network, gps);
    }

    private record AttendanceEvidence(WorkLocation workLocation, WorkLocationWifiNetwork wifi, VerifiedWorkLocation gps) {
    }

    @Override
    @Transactional(readOnly = true)
    public TodayAttendanceResponse getTodayAttendance(Long accountId) {
        Employee employee = requireEmployee(accountId);
        LocalDate workDate = attendanceClock.instant().atZone(properties.companyZoneId()).toLocalDate();

        return attendanceRecords.findByEmployeeIdAndWorkDate(employee.getId(), workDate)
                .map(this::todayResponse)
                .orElseGet(() -> TodayAttendanceResponse.builder()
                        .workDate(workDate)
                        .checkedIn(false)
                        .checkedOut(false)
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyAttendanceResponse getMonthlyAttendance(Long accountId, String month) {
        Employee employee = requireEmployee(accountId);
        YearMonth yearMonth = parseMonth(month);
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();
        List<AttendanceListItemResponse> items = attendanceRecords
                .findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDesc(employee.getId(), from, to)
                .stream()
                .map(this::listItemResponse)
                .toList();

        return MonthlyAttendanceResponse.builder()
                .month(yearMonth.toString())
                .items(items)
                .build();
    }

    private Employee requireWorkingEmployee(Long accountId) {
        Employee employee = requireEmployee(accountId);
        Employee lockedEmployee = employees.findByIdForUpdate(employee.getId())
                .orElseThrow(() -> new AuthException("ATTENDANCE_EMPLOYEE_NOT_FOUND", "Tài khoản không có hồ sơ nhân viên.", 403));
        if (lockedEmployee.getEmploymentStatus() != EmploymentStatus.WORKING) {
            throw new AuthException("ATTENDANCE_EMPLOYEE_INACTIVE", "Nhân viên không ở trạng thái được phép chấm công.", 403);
        }
        return lockedEmployee;
    }

    private Employee requireEmployee(Long accountId) {
        return employees.findByAccountId(accountId)
                .orElseThrow(() -> new AuthException("ATTENDANCE_EMPLOYEE_NOT_FOUND", "Tài khoản không có hồ sơ nhân viên.", 403));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new AuthException("VALIDATION_ERROR", "month là bắt buộc và phải có định dạng YYYY-MM.", 400);
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw new AuthException("VALIDATION_ERROR", "month phải có định dạng YYYY-MM hợp lệ.", 400);
        }
    }

    private TodayAttendanceResponse todayResponse(AttendanceRecord record) {
        return TodayAttendanceResponse.builder()
                .workDate(record.getWorkDate())
                .checkInAt(record.getCheckInAt())
                .checkOutAt(record.getCheckOutAt())
                .workingMinutes(record.getWorkingMinutes())
                .attendanceStatus(record.getAttendanceStatus())
                .checkedIn(record.getCheckInAt() != null)
                .checkedOut(record.getCheckOutAt() != null)
                .build();
    }

    private AttendanceListItemResponse listItemResponse(AttendanceRecord record) {
        return AttendanceListItemResponse.builder()
                .workDate(record.getWorkDate())
                .checkInAt(record.getCheckInAt())
                .checkOutAt(record.getCheckOutAt())
                .workingMinutes(record.getWorkingMinutes())
                .attendanceStatus(record.getAttendanceStatus())
                .build();
    }

    private AttendanceActionResponse response(AttendanceRecord record, String message) {
        return AttendanceActionResponse.builder()
                .attendanceRecordId(record.getId())
                .workDate(record.getWorkDate())
                .checkInAt(record.getCheckInAt())
                .checkOutAt(record.getCheckOutAt())
                .workingMinutes(record.getWorkingMinutes())
                .message(message)
                .build();
    }

    private AuthException conflict(String code, String message) {
        return new AuthException(code, message, 409);
    }
}
