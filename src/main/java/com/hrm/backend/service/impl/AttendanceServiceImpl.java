package com.hrm.backend.service.impl;

import com.hrm.backend.config.AttendanceProperties;
import com.hrm.backend.dto.request.AttendanceLocationRequest;
import com.hrm.backend.dto.response.AttendanceActionResponse;
import com.hrm.backend.dto.response.AttendanceListItemResponse;
import com.hrm.backend.dto.response.MonthlyAttendanceResponse;
import com.hrm.backend.dto.response.TodayAttendanceResponse;
import com.hrm.backend.entity.AttendanceRecord;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.enums.AttendanceStatus;
import com.hrm.backend.entity.enums.EmploymentStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.repository.AttendanceRecordRepository;
import com.hrm.backend.repository.EmployeeRepository;
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

    @Override
    @Transactional
    public AttendanceActionResponse checkIn(Long accountId, AttendanceLocationRequest location, HttpServletRequest request) {
        Employee employee = requireWorkingEmployee(accountId);
        VerifiedCompanyWifi wifi = wifiVerifier.verify(request);
        VerifiedWorkLocation verifiedLocation = workLocationVerifier.verify(location, wifi.network().getWorkLocation());
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
        record.setCheckInWifiSsid(wifi.network().getSsid());
        record.setCheckInWorkLocation(wifi.network().getWorkLocation());
        record.setCheckInWifiNetwork(wifi.network());
        record.setCheckInLatitude(verifiedLocation.latitude());
        record.setCheckInLongitude(verifiedLocation.longitude());
        record.setCheckInLocationDistanceMeters(verifiedLocation.distanceMeters());
        AttendanceRecord saved = attendanceRecords.save(record);
        return response(saved, "Check-in thành công.");
    }

    @Override
    @Transactional
    public AttendanceActionResponse checkOut(Long accountId, AttendanceLocationRequest location, HttpServletRequest request) {
        Employee employee = requireWorkingEmployee(accountId);
        VerifiedCompanyWifi wifi = wifiVerifier.verify(request);
        VerifiedWorkLocation verifiedLocation = workLocationVerifier.verify(location, wifi.network().getWorkLocation());
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
        record.setCheckOutWifiSsid(wifi.network().getSsid());
        record.setCheckOutWorkLocation(wifi.network().getWorkLocation());
        record.setCheckOutWifiNetwork(wifi.network());
        record.setCheckOutLatitude(verifiedLocation.latitude());
        record.setCheckOutLongitude(verifiedLocation.longitude());
        record.setCheckOutLocationDistanceMeters(verifiedLocation.distanceMeters());
        record.setWorkingMinutes((int) minutes);
        AttendanceRecord saved = attendanceRecords.save(record);
        return response(saved, "Check-out thành công.");
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
