package com.hrm.backend.controller;

import com.hrm.backend.dto.request.AttendanceLocationRequest;
import com.hrm.backend.dto.response.AttendanceActionResponse;
import com.hrm.backend.entity.Account;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceControllerTest {

    @Test
    void preCheckUsesAuthenticatedAccountAndReturnsReadOnlyResult() {
        AttendanceService service = mock(AttendanceService.class);
        var controller = new AttendanceController(service);
        Account account = Account.builder().id(42L).username("employee01").build();
        var principal = new CustomUserDetails(account, List.of("EMPLOYEE"));
        var location = new AttendanceLocationRequest(null, null, null, null);
        var request = mock(HttpServletRequest.class);
        var expected = new com.hrm.backend.dto.response.AttendancePrecheckResponse(true, "OR", true, false,
                OffsetDateTime.parse("2026-11-26T08:00:00+07:00"));
        when(service.preCheck(42L, location, request)).thenReturn(expected);
        var response = controller.preCheck(principal, location, request);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        verify(service).preCheck(42L, location, request);
    }

    @Test
    void checkIn_usesAuthenticatedAccountInsteadOfClientEmployeeId() {
        AttendanceService attendanceService = mock(AttendanceService.class);
        AttendanceController controller = new AttendanceController(attendanceService);
        Account account = Account.builder().id(42L).username("employee01").build();
        CustomUserDetails principal = new CustomUserDetails(account, List.of("EMPLOYEE"));
        AttendanceLocationRequest request = new AttendanceLocationRequest(
                new BigDecimal("10.776889"), new BigDecimal("106.700806"), new BigDecimal("10"), Instant.now());
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        AttendanceActionResponse expected = AttendanceActionResponse.builder()
                .attendanceRecordId(100L)
                .workDate(LocalDate.of(2026, 10, 8))
                .checkInAt(OffsetDateTime.parse("2026-10-08T08:15:20+07:00"))
                .message("Check-in thành công.")
                .build();
        when(attendanceService.checkIn(42L, request, httpRequest)).thenReturn(expected);

        var response = controller.checkIn(principal, request, httpRequest);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        verify(attendanceService).checkIn(42L, request, httpRequest);
    }

    @Test
    void checkOut_returnsServerCalculatedWorkingMinutesForAuthenticatedAccount() {
        AttendanceService attendanceService = mock(AttendanceService.class);
        AttendanceController controller = new AttendanceController(attendanceService);
        Account account = Account.builder().id(42L).username("employee01").build();
        CustomUserDetails principal = new CustomUserDetails(account, List.of("EMPLOYEE"));
        AttendanceLocationRequest request = new AttendanceLocationRequest(
                new BigDecimal("10.776889"), new BigDecimal("106.700806"), new BigDecimal("10"), Instant.now());
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        AttendanceActionResponse expected = AttendanceActionResponse.builder()
                .attendanceRecordId(100L)
                .workDate(LocalDate.of(2026, 10, 8))
                .checkInAt(OffsetDateTime.parse("2026-10-08T08:15:20+07:00"))
                .checkOutAt(OffsetDateTime.parse("2026-10-08T17:31:42+07:00"))
                .workingMinutes(556)
                .message("Check-out thành công.")
                .build();
        when(attendanceService.checkOut(42L, request, httpRequest)).thenReturn(expected);

        var response = controller.checkOut(principal, request, httpRequest);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(556, response.getBody().getWorkingMinutes());
        verify(attendanceService).checkOut(42L, request, httpRequest);
    }
}
