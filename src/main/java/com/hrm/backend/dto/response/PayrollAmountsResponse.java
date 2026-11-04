package com.hrm.backend.dto.response;

import java.math.BigDecimal;

public record PayrollAmountsResponse(BigDecimal baseSalaryMonthly, BigDecimal allowanceMonthly,
                                     BigDecimal deductionMonthly, BigDecimal earnedBaseSalary,
                                     BigDecimal earnedAllowance, BigDecimal earnedDeduction,
                                     BigDecimal netSalary) { }
