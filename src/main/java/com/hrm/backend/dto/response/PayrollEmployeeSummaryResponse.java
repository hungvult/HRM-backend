package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PayrollEmployeeSummaryResponse {
    Long id;
    String employeeCode;
    String fullName;
    PayrollDepartmentSnapshotResponse department;
}
