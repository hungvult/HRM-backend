package com.hrm.backend.mapper;

import com.hrm.backend.dto.response.DepartmentDto;
import com.hrm.backend.dto.response.EmployeeListItemResponse;
import com.hrm.backend.dto.response.PositionDto;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.EmployeeAssignment;
import org.springframework.stereotype.Component;

@Component
public class EmployeeListMapper {

    public EmployeeListItemResponse toResponse(Employee employee, EmployeeAssignment assignment) {
        return EmployeeListItemResponse.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .hireDate(employee.getHireDate())
                .employmentStatus(employee.getEmploymentStatus().name())
                .department(assignment == null || assignment.getDepartment() == null ? null : DepartmentDto.builder()
                        .id(assignment.getDepartment().getId())
                        .code(assignment.getDepartment().getCode())
                        .name(assignment.getDepartment().getName())
                        .build())
                .position(assignment == null || assignment.getPosition() == null ? null : PositionDto.builder()
                        .id(assignment.getPosition().getId())
                        .code(assignment.getPosition().getCode())
                        .name(assignment.getPosition().getName())
                        .build())
                .build();
    }
}
