package com.hrm.backend.mapper;

import com.hrm.backend.dto.request.CreateEmployeeRequest;
import com.hrm.backend.dto.response.EmployeeDto;
import com.hrm.backend.entity.Employee;
import org.springframework.stereotype.Component;

/** Maps employee profile data only; service code owns generated and audit fields. */
@Component
public class EmployeeMapper {

    public Employee toNewEntity(CreateEmployeeRequest request) {
        return Employee.builder()
                .fullName(request.getFullName().trim())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .email(normalizeEmail(request.getEmail()))
                .phone(request.getPhone().trim())
                .address(normalizeNullable(request.getAddress()))
                .hireDate(request.getHireDate())
                .build();
    }

    public EmployeeDto toDto(Employee employee) {
        return EmployeeDto.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .dateOfBirth(employee.getDateOfBirth())
                .gender(employee.getGender() == null ? null : employee.getGender().name())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .address(employee.getAddress())
                .hireDate(employee.getHireDate())
                .employmentStatus(employee.getEmploymentStatus() == null ? null : employee.getEmploymentStatus().name())
                .build();
    }

    public String normalizeEmail(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }
}
