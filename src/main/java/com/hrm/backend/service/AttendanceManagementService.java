package com.hrm.backend.service;

import com.hrm.backend.dto.response.AttendanceDetailResponse;
import com.hrm.backend.dto.response.AttendanceAdjustmentHistoryResponse;
import com.hrm.backend.dto.response.AttendanceManagementListItemResponse;
import com.hrm.backend.dto.response.AttendanceMutationResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.request.AdjustAttendanceRequest;
import com.hrm.backend.dto.request.CreateAttendanceRequest;
import com.hrm.backend.entity.enums.AttendanceStatus;

import java.time.LocalDate;

public interface AttendanceManagementService {
    PagedResponse<AttendanceManagementListItemResponse> searchAttendances(Long actorAccountId, String q, Long employeeId,
                                                                            Long departmentId, AttendanceStatus status,
                                                                            LocalDate fromDate, LocalDate toDate, int page,
                                                                            int size, String sortBy, String sortDirection);

    AttendanceDetailResponse getAttendance(Long actorAccountId, Long attendanceId);

    AttendanceMutationResponse createAttendance(Long actorAccountId, CreateAttendanceRequest request);

    AttendanceMutationResponse adjustAttendance(Long actorAccountId, Long attendanceId, AdjustAttendanceRequest request);

    PagedResponse<AttendanceAdjustmentHistoryResponse> getAdjustmentHistory(Long actorAccountId, Long attendanceId,
                                                                              int page, int size);
}
