package com.hrm.backend.dto.response;

import com.hrm.backend.entity.enums.SalaryProfileState;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SalaryProfileEmployeeListItemResponse {
    PayrollEmployeeSummaryResponse employee;
    SalaryProfileState profileState;
    CurrentSalaryProfileResponse currentProfile;
}
