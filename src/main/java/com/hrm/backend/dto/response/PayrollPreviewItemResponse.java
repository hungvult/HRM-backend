package com.hrm.backend.dto.response;

public record PayrollPreviewItemResponse(PayrollEmployeeSummaryResponse employee, PayrollWorkResponse work,
                                         PayrollAmountsResponse amounts, Long salaryProfileId) { }
