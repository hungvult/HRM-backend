package com.hrm.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmployeeDto {
    private Long id;
    private String employeeCode;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String email;
    private String phone;
    private String address;
    private LocalDate hireDate;
    private String employmentStatus;
    
    private DepartmentDto department;
    private PositionDto position;
    private ManagerDto manager;

    @Data
    @Builder
    public static class ManagerDto {
        private Long id;
        private String employeeCode;
        private String fullName;
    }
}
