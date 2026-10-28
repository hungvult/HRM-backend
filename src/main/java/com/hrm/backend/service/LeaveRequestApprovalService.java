package com.hrm.backend.service;

import com.hrm.backend.dto.response.LeaveRequestApprovalResponse;

public interface LeaveRequestApprovalService {
    LeaveRequestApprovalResponse approve(Long accountId, Long leaveRequestId);
}
