package com.hrm.backend.service;

import com.hrm.backend.dto.response.*;
import com.hrm.backend.entity.enums.LeaveRequestStatus;

import java.time.LocalDate;
import java.util.List;

public interface LeaveQueryService {
    List<LeaveTypeResponse> getActiveLeaveTypes();
    LeaveBalanceListResponse getMyLeaveBalances(Long accountId, int year);
    PagedResponse<LeaveRequestListItemResponse> getMyLeaveRequests(Long accountId, LeaveRequestStatus status,
                                                                     Long leaveTypeId, LocalDate fromDate, LocalDate toDate,
                                                                     int page, int size);
    PagedResponse<LeaveRequestListItemResponse> getScopedLeaveRequests(Long accountId, LeaveRequestStatus status,
                                                                         Long leaveTypeId, LocalDate fromDate, LocalDate toDate,
                                                                         int page, int size);
    LeaveRequestDetailResponse getLeaveRequestDetail(Long accountId, Long leaveRequestId);
}
