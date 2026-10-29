package com.hrm.backend.service.impl;

import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.response.PayrollDepartmentSnapshotResponse;
import com.hrm.backend.dto.response.PayrollEmployeeSummaryResponse;
import com.hrm.backend.dto.response.PayrollViewResponses;
import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.PayrollItem;
import com.hrm.backend.entity.PayrollRun;
import com.hrm.backend.entity.enums.PayrollRunStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.PayrollItemViewRepository;
import com.hrm.backend.repository.PayrollRunViewRepository;
import com.hrm.backend.service.PayrollViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollViewServiceImpl implements PayrollViewService {
    private static final int MAX_PAGE_SIZE = 100;

    private final PayrollRunViewRepository payrollRuns;
    private final PayrollItemViewRepository payrollItems;
    private final EmployeeRepository employees;

    @Override
    public PayrollViewResponses.RunDetail getRun(Long runId) {
        PayrollRun run = findRun(runId);
        return toRunDetail(run, payrollItems.summarizeByRunId(run.getId()));
    }

    @Override
    public PayrollViewResponses.ItemPage getRunItems(Long runId, String q, Long departmentId, int page, int size) {
        PayrollRun run = findRun(runId);
        validatePage(page, size);
        String normalizedQuery = normalizeQuery(q);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("employeeCodeSnapshot").ascending()
                .and(Sort.by("employee.id").ascending()));
        Page<PayrollItem> items = payrollItems.findPageByRunSnapshotFilters(
                run.getId(), normalizedQuery, departmentId, pageable);
        PayrollViewResponses.Summary summary = toSummary(
                payrollItems.summarizeByRunSnapshotFilters(run.getId(), normalizedQuery, departmentId));
        return new PayrollViewResponses.ItemPage(items.getContent().stream().map(item -> toItemDetail(run, item)).toList(),
                items.getNumber(), items.getSize(), items.getTotalElements(), items.getTotalPages(), items.hasNext(), summary);
    }

    @Override
    public PagedResponse<PayrollViewResponses.PayslipListItem> getMyPayslips(
            Long accountId, Short year, int page, int size) {
        validatePage(page, size);
        validateYear(year);
        return employees.findByAccountId(accountId)
                .map(employee -> {
                    Page<PayrollItem> items = payrollItems.findPublishedByEmployee(employee.getId(), PayrollRunStatus.PUBLISHED,
                            year, PageRequest.of(page, size, Sort.by("payrollRun.payrollYear").descending()
                                    .and(Sort.by("payrollRun.payrollMonth").descending())
                                    .and(Sort.by("payrollRun.id").descending())));
                    return PagedResponse.<PayrollViewResponses.PayslipListItem>builder()
                            .content(items.getContent().stream().map(this::toPayslipListItem).toList())
                            .page(items.getNumber()).size(items.getSize()).totalElements(items.getTotalElements())
                            .totalPages(items.getTotalPages()).hasNext(items.hasNext()).build();
                })
                .orElseGet(() -> emptyPage(page, size));
    }

    @Override
    public PayrollViewResponses.PayslipDetail getMyPayslip(Long accountId, Long payrollRunId) {
        return employees.findByAccountId(accountId)
                .flatMap(employee -> payrollItems.findPublishedByRunAndEmployee(
                        payrollRunId, employee.getId(), PayrollRunStatus.PUBLISHED))
                .map(this::toPayslipDetail)
                .orElseThrow(() -> new ResourceNotFoundException("PAYSLIP_NOT_AVAILABLE", "Phiếu lương chưa khả dụng."));
    }

    private PayrollRun findRun(Long runId) {
        return payrollRuns.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("PAYROLL_RUN_NOT_FOUND", "Không tìm thấy bảng lương."));
    }

    private PayrollViewResponses.RunDetail toRunDetail(PayrollRun run, PayrollItemViewRepository.Totals totals) {
        return new PayrollViewResponses.RunDetail(run.getId(), new PayrollViewResponses.Period(run.getPayrollYear(), run.getPayrollMonth()),
                run.getStatus().name(), new PayrollViewResponses.WorkStandard(run.getStandardWorkingDays(), run.getStandardMinutesPerDay()),
                toSummary(totals), toAccountSnapshot(run.getGeneratedByAccount()), run.getGeneratedAt(),
                toAccountSnapshot(run.getPublishedByAccount()), run.getPublishedAt());
    }

    private PayrollViewResponses.ItemDetail toItemDetail(PayrollRun run, PayrollItem item) {
        return new PayrollViewResponses.ItemDetail(item.getId(), employeeSnapshot(item),
                new PayrollViewResponses.Work(run.getStandardWorkingDays(), run.getStandardMinutesPerDay(),
                        item.getPaidMinutes(), item.getPaidWorkingDays()), amounts(item),
                item.getSalaryProfile() == null ? null : item.getSalaryProfile().getId());
    }

    private PayrollViewResponses.PayslipDetail toPayslipDetail(PayrollItem item) {
        PayrollRun run = item.getPayrollRun();
        return new PayrollViewResponses.PayslipDetail(item.getId(), employeeSnapshot(item),
                new PayrollViewResponses.Work(run.getStandardWorkingDays(), run.getStandardMinutesPerDay(),
                        item.getPaidMinutes(), item.getPaidWorkingDays()), amounts(item),
                item.getSalaryProfile() == null ? null : item.getSalaryProfile().getId(),
                new PayrollViewResponses.Period(run.getPayrollYear(), run.getPayrollMonth()), run.getPublishedAt());
    }

    private PayrollViewResponses.PayslipListItem toPayslipListItem(PayrollItem item) {
        PayrollRun run = item.getPayrollRun();
        return new PayrollViewResponses.PayslipListItem(run.getId(), run.getPayrollYear(), run.getPayrollMonth(),
                amountOrZero(item.getNetSalary()), run.getPublishedAt());
    }

    private PayrollEmployeeSummaryResponse employeeSnapshot(PayrollItem item) {
        return PayrollEmployeeSummaryResponse.builder().id(item.getEmployee().getId())
                .employeeCode(item.getEmployeeCodeSnapshot()).fullName(item.getEmployeeFullNameSnapshot())
                .department(item.getDepartmentIdSnapshot() == null ? null : PayrollDepartmentSnapshotResponse.builder()
                        .id(item.getDepartmentIdSnapshot()).code(item.getDepartmentCodeSnapshot())
                        .name(item.getDepartmentNameSnapshot()).build())
                .build();
    }

    private PayrollViewResponses.Amounts amounts(PayrollItem item) {
        return new PayrollViewResponses.Amounts(amountOrZero(item.getBaseSalarySnapshot()), amountOrZero(item.getAllowanceSnapshot()),
                amountOrZero(item.getDeductionSnapshot()), amountOrZero(item.getEarnedBaseSalary()),
                amountOrZero(item.getAllowanceAmount()), amountOrZero(item.getDeductionAmount()), amountOrZero(item.getNetSalary()));
    }

    private PayrollViewResponses.Summary toSummary(PayrollItemViewRepository.Totals totals) {
        return new PayrollViewResponses.Summary(totals.getEmployeeCount(), amountOrZero(totals.getTotalEarnedBaseSalary()),
                amountOrZero(totals.getTotalEarnedAllowance()), amountOrZero(totals.getTotalEarnedDeduction()),
                amountOrZero(totals.getTotalNetSalary()));
    }

    private PayrollViewResponses.AccountSnapshot toAccountSnapshot(Account account) {
        if (account == null) return null;
        String displayName = account.getEmployee() == null ? null : account.getEmployee().getFullName();
        return new PayrollViewResponses.AccountSnapshot(account.getId(), account.getUsername(), displayName);
    }

    private String normalizeQuery(String q) {
        if (q == null) return null;
        String normalized = q.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw validationError("q", "Từ khóa phải có từ 1 đến 100 ký tự.");
        }
        return normalized;
    }

    private void validateYear(Short year) {
        if (year != null && (year < 2000 || year > 2100)) {
            throw validationError("year", "Năm phải nằm trong khoảng 2000 đến 2100.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw validationError("page", "page phải không âm và size phải từ 1 đến 100.");
        }
    }

    private AuthException validationError(String field, String message) {
        return new AuthException("VALIDATION_ERROR", "Dữ liệu gửi lên không hợp lệ.", 400,
                java.util.List.of(com.hrm.backend.dto.response.ErrorResponse.FieldError.builder()
                        .field(field).message(message).build()));
    }

    private PagedResponse<PayrollViewResponses.PayslipListItem> emptyPage(int page, int size) {
        return PagedResponse.<PayrollViewResponses.PayslipListItem>builder().content(java.util.List.of())
                .page(page).size(size).totalElements(0).totalPages(0).hasNext(false).build();
    }

    private BigDecimal amountOrZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
