package com.hrm.backend.service.impl;

import com.hrm.backend.config.AttendanceProperties;
import com.hrm.backend.dto.request.AttendanceLocationRequest;
import com.hrm.backend.dto.response.AttendanceActionResponse;
import com.hrm.backend.dto.response.AttendancePrecheckResponse;
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
    @Transactional(readOnly = true)
    // Dùng khi app gửi GPS ngầm trước khi nhân viên bấm chấm công. accountId là ID accounts từ phiên đã xác thực.
    // Tìm employees theo account_id và kiểm tra đang làm việc; không khóa nhân viên vì chưa ghi công.
    // Dùng lại bộ xác minh đọc work_locations/work_location_wifi_networks và IP do proxy tin cậy cung cấp.
    // Không đọc/ghi attendance_records, không lưu kết quả làm quyền chấm công. Check-in/out luôn xác minh lại bằng chứng mới.
    // Lỗi nhân viên, GPS, Wi-Fi và cấu hình truyền nguyên về API; nhật ký HTTP vẫn do luồng logging chung ghi.
    public AttendancePrecheckResponse preCheck(Long accountId, AttendanceLocationRequest location, HttpServletRequest request) {
        Employee employee = requireEmployee(accountId);
        ensureWorking(employee);
        AttendanceEvidence evidence = verifyAttendanceEvidence(location, request);
        String mode = properties.isWifiVerificationEnabled() ? properties.getVerificationMode().name() : "GPS_ONLY";
        return new AttendancePrecheckResponse(true, mode, evidence.wifi() != null, evidence.gps() != null,
                OffsetDateTime.ofInstant(attendanceClock.instant(), properties.companyZoneId()));
    }

    @Override
    @Transactional
    // accountId là ID accounts từ principal đã xác thực; location là GPS app gửi, không chứa giờ công hoặc địa điểm do client chọn.
    // Khóa nhân viên đang làm việc rồi xác minh Wi-Fi/GPS theo chế độ server; lỗi thì dừng trước khi lưu công.
    // Ngày công lấy theo attendanceClock và múi giờ công ty. Khóa attendance_records theo employee_id/work_date để chặn chấm trùng.
    // Sau kiểm tra trạng thái mới ghi check_in_at, địa điểm và từng nhóm bằng chứng thực sự đã xác minh.
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
        // Chỉ ghi nhóm đã xác minh. Wi-Fi-only để trống toàn bộ GPS, kể cả GPS app gửi nhưng không đạt.
        record.setCheckInWifiSsid(evidence.wifi() == null ? null : evidence.wifi().getSsid());
        record.setCheckInWorkLocation(evidence.workLocation());
        record.setCheckInWifiNetwork(evidence.wifi());
        record.setCheckInLatitude(verifiedLocation == null ? null : verifiedLocation.latitude());
        record.setCheckInLongitude(verifiedLocation == null ? null : verifiedLocation.longitude());
        record.setCheckInGpsAccuracyMeters(verifiedLocation == null ? null : location.getAccuracyMeters());
        record.setCheckInLocationCapturedAt(verifiedLocation == null ? null : OffsetDateTime.ofInstant(location.getLocationCapturedAt(), zone));
        record.setCheckInLocationDistanceMeters(verifiedLocation == null ? null : verifiedLocation.distanceMeters());
        AttendanceRecord saved = attendanceRecords.save(record);
        return response(saved, "Check-in thành công.");
    }

    @Override
    @Transactional
    // accountId là ID accounts từ principal; location là GPS mới do app lấy khi người dùng bấm check-out.
    // Xác minh nhân viên và Wi-Fi/GPS trước, rồi khóa attendance_records theo nhân viên/ngày công từ giờ server và múi giờ công ty.
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
        record.setCheckOutLatitude(verifiedLocation == null ? null : verifiedLocation.latitude());
        record.setCheckOutLongitude(verifiedLocation == null ? null : verifiedLocation.longitude());
        record.setCheckOutGpsAccuracyMeters(verifiedLocation == null ? null : location.getAccuracyMeters());
        record.setCheckOutLocationCapturedAt(verifiedLocation == null ? null : OffsetDateTime.ofInstant(location.getLocationCapturedAt(), zone));
        record.setCheckOutLocationDistanceMeters(verifiedLocation == null ? null : verifiedLocation.distanceMeters());
        record.setWorkingMinutes((int) minutes);
        AttendanceRecord saved = attendanceRecords.save(record);
        return response(saved, "Check-out thành công.");
    }

    // Dùng cho kiểm tra ngầm và trước khi ghi check-in/out: request chứa IP từ kết nối/proxy tin cậy, location là GPS app lấy.
    // AND giữ luồng cũ. OR thử Wi-Fi trước; sai mạng mới tìm work_locations theo ID GPS cấu hình server.
    // Ví dụ network 8 thuộc địa điểm 2 thì GPS kèm Wi-Fi chỉ được đối chiếu địa điểm 2, không ghép hai địa điểm khác nhau.
    // Chỉ bỏ qua lỗi bằng chứng đã biết trong OR; lỗi DB/cấu hình luôn dừng. Không ghi DB ở hàm này.
    // Trả địa điểm, mạng đã xác minh (có thể null), GPS đã xác minh (có thể null) để caller lưu đúng nhóm.
    private AttendanceEvidence verifyAttendanceEvidence(AttendanceLocationRequest location, HttpServletRequest request) {
        WorkLocationWifiNetwork network = null;
        boolean either = properties.getVerificationMode() == AttendanceProperties.VerificationMode.OR;
        // Chỉ sai IP nguồn mới được chuyển sang GPS. Lỗi cấu hình/DB không được biến thành bằng chứng không đạt.
        if (properties.isWifiVerificationEnabled()) {
            try {
                network = wifiVerifier.verify(request).network();
            } catch (AuthException ex) {
                if (!either || !"ATTENDANCE_INVALID_COMPANY_WIFI".equals(ex.getCode())) throw ex;
                if (location == null || location.getLatitude() == null) throw ex;
            }
        }
        WorkLocation workLocation = network == null ? configuredGpsLocation() : network.getWorkLocation();
        if (either && network != null && (location == null || location.getLatitude() == null)) {
            return new AttendanceEvidence(workLocation, network, null);
        }
        VerifiedWorkLocation gps;
        // Wi-Fi đã đạt trong OR thì GPS ngoài vùng/cũ không chặn chấm công, nhưng không được lưu GPS thất bại.
        try {
            gps = workLocationVerifier.verify(location, workLocation);
        } catch (AuthException ex) {
            if (!either || network == null || !("ATTENDANCE_INVALID_GPS".equals(ex.getCode())
                    || "ATTENDANCE_OUTSIDE_WORK_LOCATION".equals(ex.getCode()))) throw ex;
            gps = null;
        }
        return new AttendanceEvidence(workLocation, network, gps);
    }

    // GPS-only hoặc OR sai Wi-Fi lấy ID work_locations từ server; không nhận ID địa điểm từ client.
    // Chỉ đọc địa điểm đang hoạt động; thiếu cấu hình trả lỗi hệ thống để không bỏ qua sai cấu hình.
    private WorkLocation configuredGpsLocation() {
        return workLocations.findById(properties.getGpsWorkLocationId())
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                .orElseThrow(() -> new IllegalStateException("Địa điểm xác minh GPS chưa được cấu hình hoặc đã vô hiệu hóa."));
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

    // Chỉ dùng ở thao tác ghi công: accountId thuộc accounts của phiên, tìm hồ sơ rồi khóa đúng employees.id.
    // Kiểm tra WORKING sau khi khóa để thay đổi trạng thái đồng thời không vượt qua điều kiện ghi công.
    private Employee requireWorkingEmployee(Long accountId) {
        Employee employee = requireEmployee(accountId);
        Employee lockedEmployee = employees.findByIdForUpdate(employee.getId())
                .orElseThrow(() -> new AuthException("ATTENDANCE_EMPLOYEE_NOT_FOUND", "Tài khoản không có hồ sơ nhân viên.", 403));
        // Dùng cùng điều kiện với kiểm tra ngầm, nhưng ở đây dữ liệu đã được khóa cho transaction ghi công.
        ensureWorking(lockedEmployee);
        return lockedEmployee;
    }

    // Cả kiểm tra ngầm và ghi công đều từ chối nhân viên không còn đang làm việc; caller quyết định có cần khóa DB.
    // Hàm chỉ kiểm tra trạng thái employees đã đọc, không cập nhật hồ sơ hoặc lịch sử.
    private void ensureWorking(Employee employee) {
        if (employee.getEmploymentStatus() != EmploymentStatus.WORKING) {
            throw new AuthException("ATTENDANCE_EMPLOYEE_INACTIVE", "Nhân viên không ở trạng thái được phép chấm công.", 403);
        }
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
