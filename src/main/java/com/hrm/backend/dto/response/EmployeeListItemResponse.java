package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class EmployeeListItemResponse {
    private Long id;
    private String employeeCode;
    private String fullName;
    private String email;
    private String phone;
    private LocalDate hireDate;
    private String employmentStatus;
    private DepartmentDto department;
    private PositionDto position;
}
