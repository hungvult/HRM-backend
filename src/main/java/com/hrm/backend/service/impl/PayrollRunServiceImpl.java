package com.hrm.backend.service.impl;

import com.hrm.backend.dto.request.*;
import com.hrm.backend.dto.response.*;
import com.hrm.backend.entity.*;
import com.hrm.backend.entity.enums.*;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.*;
import com.hrm.backend.service.PayrollRunService;
import com.hrm.backend.service.PayrollConfirmationResult;
import com.hrm.backend.service.PayrollMoneyCalculator;
import com.hrm.backend.service.PayrollPublicationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollRunServiceImpl implements PayrollRunService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999");
    private static final String RULE_VERSION = "payroll-mvp-v1";

    private final AccountRepository accounts;
    private final EmployeeRepository employees;
    private final EmployeeSalaryProfileRepository salaryProfiles;
    private final EmployeeAssignmentRepository assignments;
    private final AttendanceRecordRepository attendance;
    private final LeaveRequestRepository leaveRequests;
    private final PayrollRunRepository payrollRuns;
    private final PayrollItemRepository payrollItems;
    private final PayrollPreviewRepository previews;
    @PersistenceContext private EntityManager entityManager;

    @Value("${app.payroll.standard-minutes-per-day:0}")
    private int standardMinutesPerDay;

    @Override
    @Transactional
    public PayrollPreviewResponse preview(Long accountId, CreatePayrollPreviewRequest request) {
        validatePeriod(request.year(), request.month());
        Calculation calculation = calculate(request.year(), request.month());
        String token = "pprv_" + UUID.randomUUID().toString().replace("-", "");
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15);
        previews.save(PayrollPreview.builder().tokenHash(hash(token)).ownerAccount(accounts.getReferenceById(accountId))
                .payrollYear(request.year()).payrollMonth(request.month()).inputFingerprint(calculation.fingerprint)
                .expiresAt(expiresAt).build());
        return calculation.toPreview(token, expiresAt);
    }

    @Override
    @Transactional
    public PayrollConfirmationResult confirm(Long accountId, ConfirmPayrollPreviewRequest request) {
        PayrollPreview preview = previews.findByTokenHashForUpdate(hash(request.previewToken()))
                .orElseThrow(() -> conflict("PAYROLL_PREVIEW_ALREADY_USED", "Preview không tồn tại hoặc đã được dùng."));
        if (!preview.getOwnerAccount().getId().equals(accountId)) throw forbidden();
        if (preview.getCreatedPayrollRun() != null) return new PayrollConfirmationResult(toRunResponse(preview.getCreatedPayrollRun()), false);
        if (preview.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC)))
            throw new AuthException("PAYROLL_PREVIEW_EXPIRED", "Preview đã hết hạn, hãy xem trước lại.", 410);

        lockPeriod(preview.getPayrollYear(), preview.getPayrollMonth());
        Calculation calculation = calculate(preview.getPayrollYear(), preview.getPayrollMonth());
        if (!calculation.fingerprint.equals(preview.getInputFingerprint()))
            throw conflict("PAYROLL_PREVIEW_STALE", "Dữ liệu nguồn đã thay đổi, hãy xem trước lại.");
        if (!calculation.canConfirm) throw new AuthException("PAYROLL_INPUT_MISSING", "Dữ liệu tính lương chưa đầy đủ.", 422);
        payrollRuns.findByPayrollYearAndPayrollMonth(preview.getPayrollYear(), preview.getPayrollMonth()).ifPresent(run -> {
            throw periodExists(run.getId());
        });

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PayrollRun run = payrollRuns.save(PayrollRun.builder().payrollYear(preview.getPayrollYear())
                .payrollMonth(preview.getPayrollMonth()).status(PayrollRunStatus.GENERATED)
                .standardWorkingDays(calculation.standardDays).standardMinutesPerDay(standardMinutesPerDay)
                .calculationRuleVersion(RULE_VERSION).inputFingerprint(calculation.fingerprint)
                .generatedByAccount(accounts.getReferenceById(accountId)).generatedAt(now).build());
        payrollItems.saveAll(calculation.items.stream().map(item -> toEntity(run, item)).toList());
        preview.setCreatedPayrollRun(run);
        preview.setConsumedAt(now);
        return new PayrollConfirmationResult(toRunResponse(run), true);
    }

    @Override
    @Transactional
    public PayrollRunResponse publish(Long accountId, Long runId) {
        PayrollRun run = payrollRuns.findByIdForUpdate(runId)
                .orElseThrow(() -> new ResourceNotFoundException("PAYROLL_RUN_NOT_FOUND", "Không tìm thấy bảng lương."));
        PayrollPublicationPolicy.publish(run, accounts.getReferenceById(accountId), OffsetDateTime.now(ZoneOffset.UTC));
        return toRunResponse(run);
    }

    @Override
    public MyPayslipResponse myPayslip(Long accountId, Long runId) {
        Employee employee = employees.findByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("PAYSLIP_NOT_AVAILABLE", "Phiếu lương chưa khả dụng."));
        PayrollRun run = payrollRuns.findById(runId)
                .filter(r -> r.getStatus() == PayrollRunStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("PAYSLIP_NOT_AVAILABLE", "Phiếu lương chưa khả dụng."));
        PayrollItem item = payrollItems.findByPayrollRunIdAndEmployeeId(runId, employee.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PAYSLIP_NOT_AVAILABLE", "Phiếu lương chưa khả dụng."));
        return new MyPayslipResponse(run.getId(), run.getPayrollYear(), run.getPayrollMonth(), toItemResponse(item), run.getPublishedAt());
    }

    private Calculation calculate(short year, short month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());
        List<PayrollIssueResponse> issues = new ArrayList<>();
        if (YearMonth.of(year, month).isAfter(YearMonth.now(BUSINESS_ZONE)))
            throw new AuthException("PAYROLL_PERIOD_FUTURE", "Không thể xem trước kỳ lương tương lai.", 422);
        boolean closed = YearMonth.of(year, month).isBefore(YearMonth.now(BUSINESS_ZONE));
        if (!closed) issues.add(issue(null, null, "PAYROLL_PERIOD_NOT_CLOSED", "Kỳ lương chưa kết thúc."));
        int standardDays = weekdays(start, end);
        if (standardMinutesPerDay <= 0) issues.add(issue(null, null, "PAYROLL_INPUT_STANDARD_MINUTES_MISSING", "Chưa cấu hình số phút công chuẩn mỗi ngày."));

        List<Employee> scope = employees.findAll().stream().filter(e -> !e.getHireDate().isAfter(end)).toList();
        List<Long> employeeIds = scope.stream().map(Employee::getId).toList();
        Map<Long, Map<LocalDate, AttendanceRecord>> attendanceByEmployee = attendance
                .findByEmployeeIdInAndWorkDateBetween(employeeIds, start, end).stream()
                .collect(Collectors.groupingBy(a -> a.getEmployee().getId(), Collectors.toMap(AttendanceRecord::getWorkDate, a -> a)));
        Set<Long> leaveEmployeeIds = leaveRequests.findForPayrollPeriod(employeeIds, start, end).stream()
                .filter(r -> r.getStatus() == LeaveRequestStatus.PENDING || r.getStatus() == LeaveRequestStatus.APPROVED)
                .map(r -> r.getEmployee().getId()).collect(Collectors.toSet());
        List<PreviewItem> items = new ArrayList<>();
        for (Employee employee : scope) {
            PayrollEmployeeSummaryResponse snapshot = employeeSnapshot(employee);
            int before = issues.size();
            if (employee.getEmploymentStatus() != EmploymentStatus.WORKING)
                issues.add(issue(snapshot, null, "PAYROLL_INPUT_EMPLOYMENT_END_DATE_MISSING", "Nhân viên đã nghỉ nhưng thiếu ngày làm việc cuối cùng."));
            List<EmployeeSalaryProfile> matching = salaryProfiles.findByEmployeeIdOrderByEffectiveFromDescIdDesc(employee.getId()).stream()
                    .filter(p -> !p.getEffectiveFrom().isAfter(start) && (p.getEffectiveTo() == null || !p.getEffectiveTo().isBefore(start))).toList();
            if (matching.isEmpty()) issues.add(issue(snapshot, null, "PAYROLL_INPUT_SALARY_MISSING", "Thiếu hồ sơ lương áp dụng cho kỳ này."));
            if (matching.size() > 1) issues.add(issue(snapshot, null, "PAYROLL_INPUT_SALARY_AMBIGUOUS", "Có nhiều hồ sơ lương áp dụng cho kỳ này."));
            if (leaveEmployeeIds.contains(employee.getId())) issues.add(issue(snapshot, null, "PAYROLL_INPUT_PARTIAL_LEAVE_UNALLOCATED", "Đơn nghỉ cần phân bổ phút theo ngày trước khi tính lương."));
            int paidMinutes = paidMinutes(employee, start, end, attendanceByEmployee.getOrDefault(employee.getId(), Map.of()), snapshot, issues);
            if (issues.size() == before && standardMinutesPerDay > 0) {
                EmployeeSalaryProfile profile = matching.getFirst();
                PayrollAmountsResponse amounts = amounts(profile, paidMinutes, standardDays * standardMinutesPerDay);
                if (amounts.netSalary().signum() < 0 || amounts.netSalary().compareTo(MAX_AMOUNT) > 0)
                    issues.add(issue(snapshot, null, "PAYROLL_NET_SALARY_NEGATIVE", "Thực lĩnh âm hoặc vượt giới hạn."));
                else items.add(new PreviewItem(employee, snapshot, profile, paidMinutes, amounts));
            }
        }
        PayrollSummaryResponse summary = summary(items);
        String fingerprint = hash(fingerprintSource(year, month, standardDays, scope, items, issues));
        return new Calculation(year, month, closed, standardDays, items, issues, fingerprint, closed && issues.isEmpty(), summary);
    }

    private int paidMinutes(Employee employee, LocalDate start, LocalDate end, Map<LocalDate, AttendanceRecord> days,
                            PayrollEmployeeSummaryResponse snapshot, List<PayrollIssueResponse> issues) {
        int result = 0;
        LocalDate from = employee.getHireDate().isAfter(start) ? employee.getHireDate() : start;
        for (LocalDate date = from; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() > 5) continue;
            AttendanceRecord record = days.get(date);
            if (record == null) { issues.add(issue(snapshot, date, "PAYROLL_INPUT_ATTENDANCE_MISSING", "Thiếu dữ liệu công ngày " + date + ".")); continue; }
            if (record.getAttendanceStatus() == AttendanceStatus.ABSENT) continue;
            if ((record.getAttendanceStatus() != AttendanceStatus.PRESENT && record.getAttendanceStatus() != AttendanceStatus.LATE)
                    || record.getWorkingMinutes() == null) { issues.add(issue(snapshot, date, "PAYROLL_INPUT_ATTENDANCE_INCOMPLETE", "Dữ liệu công chưa hoàn chỉnh.")); continue; }
            if (record.getWorkingMinutes() < 0) { issues.add(issue(snapshot, date, "PAYROLL_INPUT_INVALID", "Số phút công không hợp lệ.")); continue; }
            result += Math.min(record.getWorkingMinutes(), standardMinutesPerDay);
        }
        return result;
    }

    private PayrollAmountsResponse amounts(EmployeeSalaryProfile p, int q, int denominator) {
        BigDecimal earnedBase = PayrollMoneyCalculator.prorate(p.getBaseSalary(), q, denominator);
        BigDecimal earnedAllowance = PayrollMoneyCalculator.prorate(p.getFixedAllowance(), q, denominator);
        BigDecimal earnedDeduction = PayrollMoneyCalculator.prorate(p.getFixedDeduction(), q, denominator);
        return new PayrollAmountsResponse(p.getBaseSalary(), p.getFixedAllowance(), p.getFixedDeduction(),
                earnedBase, earnedAllowance, earnedDeduction,
                earnedBase.add(earnedAllowance).subtract(earnedDeduction));
    }

    private PayrollAmountsResponse finish(PayrollAmountsResponse a) { return new PayrollAmountsResponse(a.baseSalaryMonthly(), a.allowanceMonthly(), a.deductionMonthly(), a.earnedBaseSalary(), a.earnedAllowance(), a.earnedDeduction(), a.earnedBaseSalary().add(a.earnedAllowance()).subtract(a.earnedDeduction())); }

    private PayrollItem toEntity(PayrollRun run, PreviewItem i) {
        PayrollAmountsResponse a = finish(i.amounts);
        PayrollDepartmentSnapshotResponse d = i.employee.getDepartment();
        return PayrollItem.builder().payrollRun(run).employee(i.source).salaryProfile(i.profile)
                .workingDays(BigDecimal.valueOf(i.paidMinutes).divide(BigDecimal.valueOf(standardMinutesPerDay), 4, RoundingMode.HALF_UP))
                .standardWorkingDays(BigDecimal.valueOf(run.getStandardWorkingDays())).paidMinutes(i.paidMinutes)
                .paidWorkingDays(BigDecimal.valueOf(i.paidMinutes).divide(BigDecimal.valueOf(standardMinutesPerDay), 4, RoundingMode.HALF_UP))
                .baseSalarySnapshot(a.baseSalaryMonthly()).allowanceSnapshot(a.allowanceMonthly()).deductionSnapshot(a.deductionMonthly())
                .earnedBaseSalary(a.earnedBaseSalary()).allowanceAmount(a.earnedAllowance()).deductionAmount(a.earnedDeduction()).netSalary(a.netSalary())
                .employeeCodeSnapshot(i.employee.getEmployeeCode()).employeeFullNameSnapshot(i.employee.getFullName())
                .departmentIdSnapshot(d == null ? null : d.getId()).departmentCodeSnapshot(d == null ? null : d.getCode()).departmentNameSnapshot(d == null ? null : d.getName()).build();
    }

    private PayrollSummaryResponse summary(List<PreviewItem> items) {
        return new PayrollSummaryResponse(items.size(), total(items, x -> finish(x.amounts).earnedBaseSalary()), total(items, x -> finish(x.amounts).earnedAllowance()), total(items, x -> finish(x.amounts).earnedDeduction()), total(items, x -> finish(x.amounts).netSalary()));
    }
    private BigDecimal total(List<PreviewItem> items, java.util.function.Function<PreviewItem, BigDecimal> mapper) { return items.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add); }

    private PayrollRunResponse toRunResponse(PayrollRun run) {
        List<PayrollItem> items = payrollItems.findByPayrollRunId(run.getId());
        List<PreviewItem> view = items.stream().map(this::toPreviewItem).toList();
        return new PayrollRunResponse(run.getId(), run.getPayrollYear(), run.getPayrollMonth(), run.getStatus().name(), new PayrollWorkResponse(run.getStandardWorkingDays(), run.getStandardMinutesPerDay(), null, null), summary(view), run.getGeneratedByAccount().getId(), run.getGeneratedAt(), run.getPublishedByAccount() == null ? null : run.getPublishedByAccount().getId(), run.getPublishedAt());
    }
    private PreviewItem toPreviewItem(PayrollItem item) { return new PreviewItem(item.getEmployee(), PayrollEmployeeSummaryResponse.builder().id(item.getEmployee().getId()).employeeCode(item.getEmployeeCodeSnapshot()).fullName(item.getEmployeeFullNameSnapshot()).department(item.getDepartmentIdSnapshot() == null ? null : PayrollDepartmentSnapshotResponse.builder().id(item.getDepartmentIdSnapshot()).code(item.getDepartmentCodeSnapshot()).name(item.getDepartmentNameSnapshot()).build()).build(), item.getSalaryProfile(), item.getPaidMinutes(), new PayrollAmountsResponse(item.getBaseSalarySnapshot(), item.getAllowanceSnapshot(), item.getDeductionSnapshot(), item.getEarnedBaseSalary(), item.getAllowanceAmount(), item.getDeductionAmount(), item.getNetSalary())); }
    private PayrollPreviewItemResponse toItemResponse(PayrollItem item) { PreviewItem i = toPreviewItem(item); return new PayrollPreviewItemResponse(i.employee, new PayrollWorkResponse(item.getPayrollRun().getStandardWorkingDays(), item.getPayrollRun().getStandardMinutesPerDay(), i.paidMinutes, item.getPaidWorkingDays()), finish(i.amounts), i.profile == null ? null : i.profile.getId()); }
    private PayrollEmployeeSummaryResponse employeeSnapshot(Employee e) { EmployeeAssignment a = assignments.findCurrentAssignmentByEmployeeId(e.getId()).orElse(null); return PayrollEmployeeSummaryResponse.builder().id(e.getId()).employeeCode(e.getEmployeeCode()).fullName(e.getFullName()).department(a == null || a.getDepartment() == null ? null : PayrollDepartmentSnapshotResponse.builder().id(a.getDepartment().getId()).code(a.getDepartment().getCode()).name(a.getDepartment().getName()).build()).build(); }
    private PayrollIssueResponse issue(PayrollEmployeeSummaryResponse employee, LocalDate date, String code, String message) { return new PayrollIssueResponse("ERROR", code, employee, date, null, message); }
    private void validatePeriod(Short year, Short month) { if (year == null || month == null) throw new AuthException("VALIDATION_ERROR", "Thiếu kỳ lương.", 400); }
    private int weekdays(LocalDate start, LocalDate end) { int days = 0; for (LocalDate d=start; !d.isAfter(end); d=d.plusDays(1)) if (d.getDayOfWeek().getValue() <= 5) days++; return days; }
    private AuthException conflict(String code, String message) { return new AuthException(code, message, 409); }
    private AuthException forbidden() { return new AuthException("AUTH_FORBIDDEN", "Bạn không có quyền dùng preview này.", 403); }
    private AuthException periodExists(Long id) { return new AuthException("PAYROLL_PERIOD_EXISTS", "Bảng lương của kỳ này đã tồn tại: " + id, 409); }
    private String fingerprintSource(short y, short m, int c, List<Employee> employees, List<PreviewItem> items, List<PayrollIssueResponse> issues) { return y+":"+m+":"+c+":"+standardMinutesPerDay+":"+employees.stream().map(e -> e.getId()+":"+e.getUpdatedAt()).collect(Collectors.joining("|"))+":"+items+":"+issues; }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    /** PostgreSQL transaction lock serializes different previews for the same payroll period. */
    private void lockPeriod(short year, short month) {
        long lockKey = (((long) year) << 32) | (month & 0xffffffffL);
        entityManager.createNativeQuery("select pg_advisory_xact_lock(?1)").setParameter(1, lockKey).getSingleResult();
    }
    private record PreviewItem(Employee source, PayrollEmployeeSummaryResponse employee, EmployeeSalaryProfile profile, int paidMinutes, PayrollAmountsResponse amounts) { }
    private record Calculation(short year, short month, boolean closed, int standardDays, List<PreviewItem> items, List<PayrollIssueResponse> issues, String fingerprint, boolean canConfirm, PayrollSummaryResponse summary) {
        PayrollPreviewResponse toPreview(String token, OffsetDateTime expires) { PayrollWorkResponse standard = new PayrollWorkResponse(standardDays, null, null, null); return new PayrollPreviewResponse(token, expires, year, month, closed, canConfirm ? "READY" : "BLOCKED", canConfirm, standard, summary, items.stream().map(i -> new PayrollPreviewItemResponse(i.employee, new PayrollWorkResponse(standardDays, null, i.paidMinutes, null), finishAmounts(i.amounts), i.profile.getId())).toList(), issues); }
        private static PayrollAmountsResponse finishAmounts(PayrollAmountsResponse a) { return new PayrollAmountsResponse(a.baseSalaryMonthly(), a.allowanceMonthly(), a.deductionMonthly(), a.earnedBaseSalary(), a.earnedAllowance(), a.earnedDeduction(), a.earnedBaseSalary().add(a.earnedAllowance()).subtract(a.earnedDeduction())); }
    }
}
