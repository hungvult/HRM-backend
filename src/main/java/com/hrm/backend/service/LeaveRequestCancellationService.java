package com.hrm.backend.service;

import com.hrm.backend.dto.response.LeaveRequestDetailResponse;

public interface LeaveRequestCancellationService {
    LeaveRequestDetailResponse cancel(Long accountId, Long leaveRequestId);
}
