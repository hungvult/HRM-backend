package com.hrm.backend.dto.response;

import java.time.LocalDate;

public record PayrollIssueResponse(String severity, String code, PayrollEmployeeSummaryResponse employee,
                                   LocalDate date, String field, String message) { }
