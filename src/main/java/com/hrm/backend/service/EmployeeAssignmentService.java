package com.hrm.backend.service;

import com.hrm.backend.dto.request.CreateEmployeeAssignmentRequest;
import com.hrm.backend.dto.response.EmployeeAssignmentResponse;
import com.hrm.backend.dto.response.PagedResponse;

public interface EmployeeAssignmentService {
    EmployeeAssignmentResponse createAssignment(Long actorAccountId, CreateEmployeeAssignmentRequest request);
    EmployeeAssignmentResponse getCurrentAssignment(Long accountId);
    PagedResponse<EmployeeAssignmentResponse> getAssignmentHistory(Long actorAccountId, Long employeeId, int page, int size);
}
