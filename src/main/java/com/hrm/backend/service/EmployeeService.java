package com.hrm.backend.service;

import com.hrm.backend.dto.request.CreateEmployeeRequest;
import com.hrm.backend.dto.response.EmployeeDto;
import com.hrm.backend.dto.response.EmployeeListItemResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.entity.enums.EmploymentStatus;

public interface EmployeeService {
    EmployeeDto createEmployee(CreateEmployeeRequest request);
    EmployeeDto getEmployee(Long actorAccountId, Long employeeId);
    PagedResponse<EmployeeListItemResponse> searchEmployees(String q, EmploymentStatus employmentStatus,
                                                             Long departmentId, Long positionId, int page,
                                                             int size, String sortBy, String sortDirection);
}
