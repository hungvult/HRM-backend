package com.hrm.backend.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Response shapes used by the payroll and personal-payslip read APIs. */
public final class PayrollViewResponses {
    private PayrollViewResponses() {
    }

    public record Period(Short year, Short month) {
    }

    public record WorkStandard(Integer standardWorkingDays, Integer standardMinutesPerDay) {
    }

    public record Work(Integer standardWorkingDays, Integer standardMinutesPerDay,
                       Integer paidMinutes, BigDecimal paidWorkingDays) {
    }

    public record Amounts(BigDecimal baseSalaryMonthly, BigDecimal allowanceMonthly,
                          BigDecimal deductionMonthly, BigDecimal earnedBaseSalary,
                          BigDecimal earnedAllowance, BigDecimal earnedDeduction,
                          BigDecimal netSalary) {
    }

    public record Summary(long employeeCount, BigDecimal totalEarnedBaseSalary,
                          BigDecimal totalEarnedAllowance, BigDecimal totalEarnedDeduction,
                          BigDecimal totalNetSalary) {
    }

    public record AccountSnapshot(Long id, String username, String displayName) {
    }

    public record ItemDetail(Long id, PayrollEmployeeSummaryResponse employee, Work work,
                             Amounts amounts, Long salaryProfileId) {
    }

    public record RunDetail(Long id, Period period, String status, WorkStandard workStandard,
                            Summary summary, AccountSnapshot generatedBy, OffsetDateTime generatedAt,
                            AccountSnapshot publishedBy, OffsetDateTime publishedAt) {
    }

    public record ItemPage(List<ItemDetail> content, int page, int size, long totalElements,
                           int totalPages, boolean hasNext, Summary filteredSummary) {
    }

    public record PayslipListItem(Long payrollRunId, Short year, Short month,
                                  BigDecimal netSalary, OffsetDateTime publishedAt) {
    }

    public record PayslipDetail(Long id, PayrollEmployeeSummaryResponse employee, Work work,
                                Amounts amounts, Long salaryProfileId, Period period,
                                OffsetDateTime publishedAt) {
    }
}
