package com.hrm.backend.service;

import com.hrm.backend.dto.request.CreateEmployeeRequest;
import com.hrm.backend.dto.response.EmployeeDto;

public interface EmployeeService {
    EmployeeDto createEmployee(Long actorAccountId, CreateEmployeeRequest request);
    EmployeeDto getEmployee(Long actorAccountId, Long employeeId);
}
