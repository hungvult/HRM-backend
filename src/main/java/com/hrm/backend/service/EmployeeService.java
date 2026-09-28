package com.hrm.backend.service;

import com.hrm.backend.dto.request.CreateEmployeeRequest;
import com.hrm.backend.dto.request.UpdateEmployeeRequest;
import com.hrm.backend.dto.response.EmployeeDto;

public interface EmployeeService {
    EmployeeDto createEmployee(CreateEmployeeRequest request);
    EmployeeDto getEmployee(Long actorAccountId, Long employeeId);
    EmployeeDto updateEmployee(Long actorAccountId, Long employeeId, UpdateEmployeeRequest request);
}
