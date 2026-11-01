package com.hrm.backend.dto.response;

import java.math.BigDecimal;

public record PayrollSummaryResponse(long employeeCount, BigDecimal totalEarnedBaseSalary,
                                     BigDecimal totalEarnedAllowance, BigDecimal totalEarnedDeduction,
                                     BigDecimal totalNetSalary) { }
