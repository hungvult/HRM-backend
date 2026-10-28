package com.hrm.backend.service.impl;

import com.hrm.backend.config.AttendanceProperties;
import com.hrm.backend.dto.request.AttendanceLocationRequest;
import com.hrm.backend.dto.response.AttendanceActionResponse;
import com.hrm.backend.dto.response.MonthlyAttendanceResponse;
import com.hrm.backend.dto.response.TodayAttendanceResponse;
import com.hrm.backend.entity.AttendanceRecord;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.WorkLocation;
import com.hrm.backend.entity.WorkLocationWifiNetwork;
import com.hrm.backend.entity.enums.EmploymentStatus;
import com.hrm.backend.entity.enums.AttendanceStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.repository.AttendanceRecordRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.service.CompanyWifiVerifier;
import com.hrm.backend.service.VerifiedCompanyWifi;
import com.hrm.backend.service.VerifiedWorkLocation;
import com.hrm.backend.service.WorkLocationVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceServiceImplTest {

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final AttendanceRecordRepository attendanceRecords = mock(AttendanceRecordRepository.class);
    private final CompanyWifiVerifier wifiVerifier = mock(CompanyWifiVerifier.class);
    private final WorkLocationVerifier workLocationVerifier = mock(WorkLocationVerifier.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private AttendanceServiceImpl service;
    private Employee employee;
    private WorkLocationWifiNetwork wifiNetwork;
    private AttendanceLocationRequest location;

    @BeforeEach
    void setUp() throws Exception {
        AttendanceProperties properties = new AttendanceProperties();
        properties.setCompanyTimezone("Asia/Ho_Chi_Minh");
        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-06T17:30:00Z"), ZoneOffset.UTC);
        service = new AttendanceServiceImpl(employees, attendanceRecords, wifiVerifier, workLocationVerifier, properties, fixedClock);

        employee = Employee.builder().id(10L).employmentStatus(EmploymentStatus.WORKING).build();
        WorkLocation workLocation = WorkLocation.builder().id(2L).name("Trụ sở").isActive(true).build();
        wifiNetwork = WorkLocationWifiNetwork.builder()
                .id(8L)
                .ssid("HRM-CORP")
                .workLocation(workLocation)
                .isActive(true)
                .build();

        when(employees.findByAccountId(1L)).thenReturn(Optional.of(employee));
        when(employees.findByIdForUpdate(10L)).thenReturn(Optional.of(employee));
        when(wifiVerifier.verify(request)).thenReturn(new VerifiedCompanyWifi(wifiNetwork, InetAddress.getByName("203.0.113.24")));
        location = new AttendanceLocationRequest(new BigDecimal("10.776889"), new BigDecimal("106.700806"),
                new BigDecimal("10"), Instant.parse("2026-10-06T17:30:00Z"));
        when(workLocationVerifier.verify(location, wifiNetwork.getWorkLocation()))
                .thenReturn(new VerifiedWorkLocation(location.getLatitude(), location.getLongitude(), 24));
        when(attendanceRecords.save(any(AttendanceRecord.class))).thenAnswer(invocation -> {
            AttendanceRecord record = invocation.getArgument(0);
            if (record.getId() == null) {
                record.setId(100L);
            }
            return record;
        });
    }

    @Test
    void checkIn_usesServerTimeAndVerifiedWifiOnly() {
        LocalDate expectedWorkDate = LocalDate.of(2026, 10, 7);
        when(attendanceRecords.findByEmployeeIdAndWorkDateForUpdate(10L, expectedWorkDate))
                .thenReturn(Optional.empty());

        AttendanceActionResponse response = service.checkIn(1L, location, request);

        assertEquals(expectedWorkDate, response.getWorkDate());
        assertEquals("2026-10-07T00:30+07:00", response.getCheckInAt().toString());
        assertEquals("Check-in thành công.", response.getMessage());
        ArgumentCaptor<AttendanceRecord> captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(attendanceRecords).save(captor.capture());
        AttendanceRecord saved = captor.getValue();
        assertEquals("HRM-CORP", saved.getCheckInWifiSsid());
        assertEquals(wifiNetwork, saved.getCheckInWifiNetwork());
        assertEquals(wifiNetwork.getWorkLocation(), saved.getCheckInWorkLocation());
        assertEquals(location.getLatitude(), saved.getCheckInLatitude());
        assertEquals(location.getLongitude(), saved.getCheckInLongitude());
        assertEquals(location.getAccuracyMeters(), saved.getCheckInGpsAccuracyMeters());
        assertEquals("2026-10-07T00:30+07:00", saved.getCheckInLocationCapturedAt().toString());
        assertEquals(24, saved.getCheckInLocationDistanceMeters());
    }

    @Test
    void checkOut_calculatesMinutesFromServerClock() {
        AttendanceRecord existing = AttendanceRecord.builder()
                .id(100L)
                .employee(employee)
                .workDate(LocalDate.of(2026, 10, 7))
                .checkInAt(java.time.OffsetDateTime.parse("2026-10-06T14:00:00Z"))
                .build();
        when(attendanceRecords.findByEmployeeIdAndWorkDateForUpdate(eq(10L), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));

        AttendanceActionResponse response = service.checkOut(1L, location, request);

        assertEquals(210, response.getWorkingMinutes());
        assertEquals("HRM-CORP", existing.getCheckOutWifiSsid());
        assertEquals(wifiNetwork, existing.getCheckOutWifiNetwork());
        assertEquals(location.getLatitude(), existing.getCheckOutLatitude());
        assertEquals(location.getAccuracyMeters(), existing.getCheckOutGpsAccuracyMeters());
        assertEquals("2026-10-07T00:30+07:00", existing.getCheckOutLocationCapturedAt().toString());
        assertEquals(24, existing.getCheckOutLocationDistanceMeters());
    }

    @Test
    void checkIn_whenAlreadyCheckedIn_returnsVietnameseConflict() {
        AttendanceRecord existing = AttendanceRecord.builder()
                .employee(employee)
                .workDate(LocalDate.of(2026, 10, 7))
                .checkInAt(java.time.OffsetDateTime.parse("2026-10-06T17:00:00Z"))
                .build();
        when(attendanceRecords.findByEmployeeIdAndWorkDateForUpdate(eq(10L), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));

        AuthException exception = assertThrows(AuthException.class, () -> service.checkIn(1L, location, request));

        assertEquals(409, exception.getStatus());
        assertEquals("ATTENDANCE_ALREADY_CHECKED_IN", exception.getCode());
        assertEquals("Bạn đã check-in trước đó.", exception.getMessage());
    }

    @Test
    void getToday_returnsEmptyStateForAuthenticatedEmployeeWithoutAttendanceRecord() {
        LocalDate expectedWorkDate = LocalDate.of(2026, 10, 7);
        when(attendanceRecords.findByEmployeeIdAndWorkDate(10L, expectedWorkDate)).thenReturn(Optional.empty());

        TodayAttendanceResponse response = service.getTodayAttendance(1L);

        assertEquals(expectedWorkDate, response.getWorkDate());
        assertEquals(false, response.isCheckedIn());
        assertEquals(false, response.isCheckedOut());
        assertNull(response.getAttendanceStatus());
        assertNull(response.getCheckInAt());
    }

    @Test
    void getMonthly_returnsOnlyAuthenticatedEmployeesRecordsInDescendingDateOrder() {
        AttendanceRecord record = AttendanceRecord.builder()
                .employee(employee)
                .workDate(LocalDate.of(2026, 10, 6))
                .checkInAt(java.time.OffsetDateTime.parse("2026-10-06T08:00:00+07:00"))
                .checkOutAt(java.time.OffsetDateTime.parse("2026-10-06T17:00:00+07:00"))
                .workingMinutes(540)
                .attendanceStatus(AttendanceStatus.PRESENT)
                .build();
        when(attendanceRecords.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDesc(
                10L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(List.of(record));

        MonthlyAttendanceResponse response = service.getMonthlyAttendance(1L, "2026-10");

        assertEquals("2026-10", response.getMonth());
        assertEquals(1, response.getItems().size());
        assertEquals(LocalDate.of(2026, 10, 6), response.getItems().getFirst().getWorkDate());
        assertEquals(540, response.getItems().getFirst().getWorkingMinutes());
    }

    @Test
    void getMonthly_rejectsInvalidMonth() {
        AuthException exception = assertThrows(AuthException.class,
                () -> service.getMonthlyAttendance(1L, "2026-13"));

        assertEquals(400, exception.getStatus());
        assertEquals("VALIDATION_ERROR", exception.getCode());
    }
}
