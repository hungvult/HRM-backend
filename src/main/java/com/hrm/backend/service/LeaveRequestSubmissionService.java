package com.hrm.backend.service;

import com.hrm.backend.dto.request.CreateLeaveRequest;
import com.hrm.backend.dto.response.LeaveRequestSubmissionResponse;

public interface LeaveRequestSubmissionService {
    LeaveRequestSubmissionResponse submit(Long accountId, CreateLeaveRequest request);
}
