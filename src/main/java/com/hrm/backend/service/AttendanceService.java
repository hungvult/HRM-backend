package com.hrm.backend.service;

import com.hrm.backend.dto.response.AttendanceActionResponse;
import com.hrm.backend.dto.response.MonthlyAttendanceResponse;
import com.hrm.backend.dto.response.TodayAttendanceResponse;
import com.hrm.backend.dto.request.AttendanceLocationRequest;
import jakarta.servlet.http.HttpServletRequest;

public interface AttendanceService {
    AttendanceActionResponse checkIn(Long accountId, AttendanceLocationRequest location, HttpServletRequest request);

    AttendanceActionResponse checkOut(Long accountId, AttendanceLocationRequest location, HttpServletRequest request);

    TodayAttendanceResponse getTodayAttendance(Long accountId);

    MonthlyAttendanceResponse getMonthlyAttendance(Long accountId, String month);
}
