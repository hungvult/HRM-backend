package com.hrm.backend.service;

import com.hrm.backend.dto.response.AttendanceDetailResponse;
import com.hrm.backend.dto.response.AttendanceListItemResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.entity.enums.AttendanceStatus;

import java.time.LocalDate;

public interface AttendanceManagementService {
    PagedResponse<AttendanceListItemResponse> searchAttendances(Long actorAccountId, String q, Long employeeId,
                                                                  Long departmentId, AttendanceStatus status,
                                                                  LocalDate fromDate, LocalDate toDate, int page,
                                                                  int size, String sortBy, String sortDirection);

    AttendanceDetailResponse getAttendance(Long actorAccountId, Long attendanceId);
}
