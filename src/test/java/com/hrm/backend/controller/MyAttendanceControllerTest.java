package com.hrm.backend.controller;

import com.hrm.backend.dto.response.AttendanceListItemResponse;
import com.hrm.backend.dto.response.MonthlyAttendanceResponse;
import com.hrm.backend.dto.response.TodayAttendanceResponse;
import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.enums.AttendanceStatus;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.AttendanceService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyAttendanceControllerTest {

    @Test
    void getToday_usesAuthenticatedAccountOnly() {
        AttendanceService attendanceService = mock(AttendanceService.class);
        MyAttendanceController controller = new MyAttendanceController(attendanceService);
        CustomUserDetails principal = principal(42L);
        TodayAttendanceResponse expected = TodayAttendanceResponse.builder()
                .workDate(LocalDate.of(2026, 10, 8))
                .checkedIn(false)
                .checkedOut(false)
                .build();
        when(attendanceService.getTodayAttendance(42L)).thenReturn(expected);

        var response = controller.getToday(principal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        verify(attendanceService).getTodayAttendance(42L);
    }

    @Test
    void getMonthly_usesAuthenticatedAccountAndMonthParameter() {
        AttendanceService attendanceService = mock(AttendanceService.class);
        MyAttendanceController controller = new MyAttendanceController(attendanceService);
        MonthlyAttendanceResponse expected = MonthlyAttendanceResponse.builder()
                .month("2026-10")
                .items(List.of(AttendanceListItemResponse.builder()
                        .workDate(LocalDate.of(2026, 10, 7))
                        .checkInAt(OffsetDateTime.parse("2026-10-07T08:00:00+07:00"))
                        .workingMinutes(480)
                        .attendanceStatus(AttendanceStatus.PRESENT)
                        .build()))
                .build();
        when(attendanceService.getMonthlyAttendance(42L, "2026-10")).thenReturn(expected);

        var response = controller.getMonthly(principal(42L), "2026-10");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        verify(attendanceService).getMonthlyAttendance(42L, "2026-10");
    }

    private CustomUserDetails principal(Long accountId) {
        return new CustomUserDetails(Account.builder().id(accountId).username("employee01").build(), List.of("EMPLOYEE"));
    }
}
