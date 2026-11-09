package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SalaryProfileHistoryResponse {
    PayrollEmployeeSummaryResponse employee;
    List<SalaryProfileHistoryItemResponse> profiles;
}
