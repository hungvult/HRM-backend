package com.hrm.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Exact VND calculation from paid minutes; callers must never use floating point. */
public final class PayrollMoneyCalculator {
    private PayrollMoneyCalculator() { }

    public static BigDecimal prorate(BigDecimal monthlyAmount, int paidMinutes, int standardMinutes) {
        if (monthlyAmount == null || paidMinutes < 0 || standardMinutes <= 0) {
            throw new IllegalArgumentException("Invalid payroll amount or work-minute input");
        }
        return monthlyAmount.multiply(BigDecimal.valueOf(paidMinutes))
                .divide(BigDecimal.valueOf(standardMinutes), 0, RoundingMode.HALF_UP);
    }
}
