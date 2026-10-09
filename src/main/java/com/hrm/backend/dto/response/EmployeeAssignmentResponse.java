package com.hrm.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmployeeAssignmentResponse {
    private Long id;
    private EmployeeSummary employee;
    private DepartmentDto department;
    private PositionDto position;
    private EmployeeSummary manager;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Boolean isCurrent;
    private AssignedBy assignedBy;
    private OffsetDateTime createdAt;

    @Getter
    @Builder
    public static class EmployeeSummary {
        private Long id;
        private String employeeCode;
        private String fullName;
    }

    @Getter
    @Builder
    public static class AssignedBy {
        private Long accountId;
        private String username;
    }
}
