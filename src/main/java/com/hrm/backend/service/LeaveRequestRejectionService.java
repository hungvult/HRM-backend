package com.hrm.backend.service;

import com.hrm.backend.dto.request.RejectLeaveRequest;
import com.hrm.backend.dto.response.LeaveRequestRejectionResponse;

public interface LeaveRequestRejectionService {
    LeaveRequestRejectionResponse reject(Long accountId, Long leaveRequestId, RejectLeaveRequest request);
}
