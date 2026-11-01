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
        LocalDate periodEnd = YearMonth.of(preview.getPayrollYear(), preview.getPayrollMonth()).atEndOfMonth();
        List<Employee> lockedScope = employees.findEligibleForPayrollForUpdate(periodEnd);
        Calculation calculation = calculate(preview.getPayrollYear(), preview.getPayrollMonth(), lockedScope);
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
                .generatedByAccount(accounts.getReferenceById(accountId)).generatedAt(now)
                .revision(1).isCurrent(true).build());
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
        return calculate(year, month, null);
    }

    /**
     * A confirmation passes locked employees so the salary-profile write path,
     * which locks the same rows, cannot change a timeline mid-snapshot.
     */
    private Calculation calculate(short year, short month, List<Employee> lockedScope) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());
        List<PayrollIssueResponse> issues = new ArrayList<>();
        if (YearMonth.of(year, month).isAfter(YearMonth.now(BUSINESS_ZONE)))
            throw new AuthException("PAYROLL_PERIOD_FUTURE", "Không thể xem trước kỳ lương tương lai.", 422);
        boolean closed = YearMonth.of(year, month).isBefore(YearMonth.now(BUSINESS_ZONE));
        if (!closed) issues.add(issue(null, null, "PAYROLL_PERIOD_NOT_CLOSED", "Kỳ lương chưa kết thúc."));
        int standardDays = weekdays(start, end);
        if (standardMinutesPerDay <= 0) issues.add(issue(null, null, "PAYROLL_INPUT_STANDARD_MINUTES_MISSING", "Chưa cấu hình số phút công chuẩn mỗi ngày."));

        List<Employee> scope = (lockedScope == null ? employees.findAll().stream()
                .filter(e -> !e.getHireDate().isAfter(end)) : lockedScope.stream())
                .sorted(Comparator.comparing(Employee::getId)).toList();
        List<Long> employeeIds = scope.stream().map(Employee::getId).toList();
        List<AttendanceRecord> attendanceRecords = attendance.findByEmployeeIdInAndWorkDateBetween(employeeIds, start, end);
        Map<Long, Map<LocalDate, AttendanceRecord>> attendanceByEmployee = attendanceRecords.stream()
                .collect(Collectors.groupingBy(a -> a.getEmployee().getId(), Collectors.toMap(AttendanceRecord::getWorkDate, a -> a)));
        List<LeaveRequest> periodLeaves = leaveRequests.findForPayrollPeriod(employeeIds, start, end);
        Map<Long, List<LeaveRequest>> leavesByEmployee = periodLeaves.stream()
                .collect(Collectors.groupingBy(r -> r.getEmployee().getId()));
        Map<Long, EmployeeAssignment> assignmentsAtPeriod = assignmentsAtPeriod(employeeIds, start, end);
        List<PreviewItem> items = new ArrayList<>();
        for (Employee employee : scope) {
            PayrollEmployeeSummaryResponse snapshot = employeeSnapshot(employee, assignmentsAtPeriod.get(employee.getId()));
            int before = issues.size();
            if (employee.getEmploymentStatus() == EmploymentStatus.TERMINATED)
                issues.add(issue(snapshot, null, "PAYROLL_INPUT_EMPLOYMENT_END_DATE_MISSING", "Nhân viên đã nghỉ nhưng thiếu ngày làm việc cuối cùng."));
            List<EmployeeSalaryProfile> matching = salaryProfiles.findByEmployeeIdOrderByEffectiveFromDescIdDesc(employee.getId()).stream()
                    .filter(p -> !p.getEffectiveFrom().isAfter(start) && (p.getEffectiveTo() == null || !p.getEffectiveTo().isBefore(start))).toList();
            if (matching.isEmpty()) issues.add(issue(snapshot, null, "PAYROLL_INPUT_SALARY_MISSING", "Thiếu hồ sơ lương áp dụng cho kỳ này."));
            if (matching.size() > 1) issues.add(issue(snapshot, null, "PAYROLL_INPUT_SALARY_AMBIGUOUS", "Có nhiều hồ sơ lương áp dụng cho kỳ này."));
            Map<LocalDate, Boolean> approvedLeaveDays = approvedLeaveDays(employee, start, end,
                    leavesByEmployee.getOrDefault(employee.getId(), List.of()), snapshot, issues);
            int paidMinutes = paidMinutes(employee, start, end, attendanceByEmployee.getOrDefault(employee.getId(), Map.of()),
                    approvedLeaveDays, snapshot, issues);
            if (issues.size() == before && standardMinutesPerDay > 0) {
                EmployeeSalaryProfile profile = matching.getFirst();
                PayrollAmountsResponse amounts = amounts(profile, paidMinutes, standardDays * standardMinutesPerDay);
                if (amounts.netSalary().signum() < 0 || amounts.netSalary().compareTo(MAX_AMOUNT) > 0)
                    issues.add(issue(snapshot, null, "PAYROLL_NET_SALARY_NEGATIVE", "Thực lĩnh âm hoặc vượt giới hạn."));
                else items.add(new PreviewItem(employee, snapshot, profile, paidMinutes, amounts));
            }
        }
        PayrollSummaryResponse summary = summary(items);
        String fingerprint = hash(fingerprintSource(year, month, standardDays, scope, attendanceRecords,
                periodLeaves, assignmentsAtPeriod.values(), items, issues));
        return new Calculation(year, month, closed, standardDays, standardMinutesPerDay, items, issues, fingerprint,
                closed && issues.isEmpty(), summary);
    }

    private int paidMinutes(Employee employee, LocalDate start, LocalDate end, Map<LocalDate, AttendanceRecord> days,
                            Map<LocalDate, Boolean> approvedLeaveDays, PayrollEmployeeSummaryResponse snapshot,
                            List<PayrollIssueResponse> issues) {
        int result = 0;
        LocalDate from = employee.getHireDate().isAfter(start) ? employee.getHireDate() : start;
        for (LocalDate date = from; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() > 5) continue;
            AttendanceRecord record = days.get(date);
            Boolean paidLeave = approvedLeaveDays.get(date);
            if (paidLeave != null) {
                if (record != null && (record.getAttendanceStatus() == AttendanceStatus.PRESENT || record.getAttendanceStatus() == AttendanceStatus.LATE)
                        && record.getWorkingMinutes() != null && record.getWorkingMinutes() > 0) {
                    issues.add(issue(snapshot, date, "PAYROLL_INPUT_LEAVE_OVERLAP", "Ngày nghỉ đã duyệt bị trùng với dữ liệu công."));
                    continue;
                }
                if (paidLeave) result += standardMinutesPerDay;
                continue;
            }
            if (record == null) { issues.add(issue(snapshot, date, "PAYROLL_INPUT_ATTENDANCE_MISSING", "Thiếu dữ liệu công ngày " + date + ".")); continue; }
            if (record.getAttendanceStatus() == AttendanceStatus.ABSENT) continue;
            if ((record.getAttendanceStatus() != AttendanceStatus.PRESENT && record.getAttendanceStatus() != AttendanceStatus.LATE)
                    || record.getWorkingMinutes() == null) { issues.add(issue(snapshot, date, "PAYROLL_INPUT_ATTENDANCE_INCOMPLETE", "Dữ liệu công chưa hoàn chỉnh.")); continue; }
            if (record.getWorkingMinutes() < 0) { issues.add(issue(snapshot, date, "PAYROLL_INPUT_INVALID", "Số phút công không hợp lệ.")); continue; }
            result += Math.min(record.getWorkingMinutes(), standardMinutesPerDay);
        }
        return result;
    }

    /**
     * Approved full-day leave can be allocated from the existing date range and
     * requestedDays fields. Partial or otherwise ambiguous leave remains a
     * blocking issue until the leave module stores per-day minutes.
     */
    private Map<LocalDate, Boolean> approvedLeaveDays(Employee employee, LocalDate periodStart, LocalDate periodEnd,
                                                       List<LeaveRequest> requests,
                                                       PayrollEmployeeSummaryResponse snapshot,
                                                       List<PayrollIssueResponse> issues) {
        Map<LocalDate, Boolean> result = new HashMap<>();
        LocalDate employeeStart = employee.getHireDate().isAfter(periodStart) ? employee.getHireDate() : periodStart;
        for (LeaveRequest request : requests) {
            if (request.getStatus() == LeaveRequestStatus.PENDING) {
                issues.add(issue(snapshot, null, "PAYROLL_INPUT_PENDING_LEAVE", "Có đơn nghỉ đang chờ duyệt trong kỳ lương."));
                continue;
            }
            if (request.getStatus() != LeaveRequestStatus.APPROVED) continue;

            LocalDate from = max(request.getStartDate(), employeeStart);
            LocalDate to = min(request.getEndDate(), periodEnd);
            int requestedWorkingDays = weekdays(request.getStartDate(), request.getEndDate());
            if (request.getRequestedDays() == null || request.getRequestedDays().compareTo(BigDecimal.valueOf(requestedWorkingDays)) != 0) {
                issues.add(issue(snapshot, null, "PAYROLL_INPUT_PARTIAL_LEAVE_UNALLOCATED",
                        "Đơn nghỉ cần phân bổ phút theo ngày trước khi tính lương."));
                continue;
            }
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (date.getDayOfWeek().getValue() > 5) continue;
                if (result.putIfAbsent(date, Boolean.TRUE.equals(request.getLeaveType().getIsPaid())) != null) {
                    issues.add(issue(snapshot, date, "PAYROLL_INPUT_LEAVE_OVERLAP", "Có nhiều đơn nghỉ đã duyệt trùng ngày."));
                }
            }
        }
        return result;
    }

    private Map<Long, EmployeeAssignment> assignmentsAtPeriod(List<Long> employeeIds, LocalDate start, LocalDate end) {
        Map<Long, List<EmployeeAssignment>> candidates = assignments
                .findEffectiveAssignmentsForEmployeesBetween(employeeIds, start, end).stream()
                .collect(Collectors.groupingBy(a -> a.getEmployee().getId()));
        Map<Long, EmployeeAssignment> result = new HashMap<>();
        Comparator<EmployeeAssignment> byEffectiveFromThenId = Comparator
                .comparing(EmployeeAssignment::getEffectiveFrom).thenComparing(EmployeeAssignment::getId);
        for (Map.Entry<Long, List<EmployeeAssignment>> entry : candidates.entrySet()) {
            EmployeeAssignment selected = entry.getValue().stream()
                    .filter(a -> !a.getEffectiveFrom().isAfter(end)
                            && (a.getEffectiveTo() == null || !a.getEffectiveTo().isBefore(end)))
                    .max(byEffectiveFromThenId)
                    .orElseGet(() -> entry.getValue().stream().max(byEffectiveFromThenId).orElse(null));
            if (selected != null) result.put(entry.getKey(), selected);
        }
        return result;
    }

    private LocalDate max(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private LocalDate min(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
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
    private PayrollEmployeeSummaryResponse employeeSnapshot(Employee employee, EmployeeAssignment assignment) {
        return PayrollEmployeeSummaryResponse.builder()
                .id(employee.getId()).employeeCode(employee.getEmployeeCode()).fullName(employee.getFullName())
                .department(assignment == null || assignment.getDepartment() == null ? null
                        : PayrollDepartmentSnapshotResponse.builder().id(assignment.getDepartment().getId())
                        .code(assignment.getDepartment().getCode()).name(assignment.getDepartment().getName()).build())
                .build();
    }
    private PayrollIssueResponse issue(PayrollEmployeeSummaryResponse employee, LocalDate date, String code, String message) { return new PayrollIssueResponse("ERROR", code, employee, date, null, message); }
    private void validatePeriod(Short year, Short month) { if (year == null || month == null) throw new AuthException("VALIDATION_ERROR", "Thiếu kỳ lương.", 400); }
    private int weekdays(LocalDate start, LocalDate end) { int days = 0; for (LocalDate d=start; !d.isAfter(end); d=d.plusDays(1)) if (d.getDayOfWeek().getValue() <= 5) days++; return days; }
    private AuthException conflict(String code, String message) { return new AuthException(code, message, 409); }
    private AuthException forbidden() { return new AuthException("AUTH_FORBIDDEN", "Bạn không có quyền dùng preview này.", 403); }
    private AuthException periodExists(Long id) { return new AuthException("PAYROLL_PERIOD_EXISTS", "Bảng lương của kỳ này đã tồn tại: " + id, 409); }
    /**
     * Fingerprints must contain scalar source facts only. Entity toString()
     * falls back to object identity, which changes between preview and confirm
     * and would make every preview look stale.
     */
    private String fingerprintSource(short year, short month, int standardDays, List<Employee> employees,
                                     List<AttendanceRecord> attendanceRecords, List<LeaveRequest> periodLeaves,
                                     Collection<EmployeeAssignment> assignmentsAtPeriod,
                                     List<PreviewItem> items, List<PayrollIssueResponse> issues) {
        StringBuilder source = new StringBuilder();
        appendFact(source, "period", year, month, standardDays, standardMinutesPerDay);
        employees.stream().sorted(Comparator.comparing(Employee::getId)).forEach(employee ->
                appendFact(source, "employee", employee.getId(), employee.getEmployeeCode(), employee.getFullName(),
                        employee.getHireDate(), employee.getEmploymentStatus(), employee.getUpdatedAt()));
        attendanceRecords.stream().sorted(Comparator.comparing((AttendanceRecord record) -> record.getEmployee().getId())
                        .thenComparing(AttendanceRecord::getWorkDate).thenComparing(AttendanceRecord::getId))
                .forEach(record -> appendFact(source, "attendance", record.getId(), record.getEmployee().getId(),
                        record.getWorkDate(), record.getAttendanceStatus(), record.getWorkingMinutes(), record.getUpdatedAt()));
        periodLeaves.stream().sorted(Comparator.comparing(LeaveRequest::getId)).forEach(request ->
                appendFact(source, "leave", request.getId(), request.getEmployee().getId(), request.getLeaveType().getId(),
                        request.getLeaveType().getIsPaid(), request.getStartDate(), request.getEndDate(),
                        decimal(request.getRequestedDays()), request.getStatus(), request.getUpdatedAt()));
        assignmentsAtPeriod.stream().sorted(Comparator.comparing(EmployeeAssignment::getId)).forEach(assignment ->
                appendFact(source, "assignment", assignment.getId(), assignment.getEmployee().getId(),
                        assignment.getDepartment() == null ? null : assignment.getDepartment().getId(),
                        assignment.getDepartment() == null ? null : assignment.getDepartment().getCode(),
                        assignment.getDepartment() == null ? null : assignment.getDepartment().getName(),
                        assignment.getEffectiveFrom(), assignment.getEffectiveTo(), assignment.getIsCurrent()));
        items.stream().sorted(Comparator.comparing(item -> item.source().getId())).forEach(item -> appendFact(source, "item",
                item.source().getId(), snapshot(item.employee()), profile(item.profile()), item.paidMinutes(), amounts(item.amounts())));
        issues.stream().sorted(Comparator.comparing(PayrollIssueResponse::code)
                        .thenComparing(issue -> issue.employee() == null ? Long.MIN_VALUE : issue.employee().getId())
                        .thenComparing(PayrollIssueResponse::date, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(PayrollIssueResponse::message))
                .forEach(issue -> appendFact(source, "issue", issue.severity(), issue.code(), snapshot(issue.employee()),
                        issue.date(), issue.field(), issue.message()));
        return source.toString();
    }

    private void appendFact(StringBuilder source, String kind, Object... values) {
        source.append(kind);
        for (Object value : values) {
            String text = String.valueOf(value);
            source.append('|').append(text.length()).append(':').append(text);
        }
        source.append('\n');
    }

    private String snapshot(PayrollEmployeeSummaryResponse employee) {
        if (employee == null) return "-";
        PayrollDepartmentSnapshotResponse department = employee.getDepartment();
        return employee.getId() + "/" + employee.getEmployeeCode() + "/" + employee.getFullName() + "/"
                + (department == null ? "-" : department.getId() + "/" + department.getCode() + "/" + department.getName());
    }

    private String profile(EmployeeSalaryProfile profile) {
        return profile == null ? "-" : profile.getId() + "/" + profile.getEffectiveFrom() + "/" + profile.getEffectiveTo()
                + "/" + decimal(profile.getBaseSalary()) + "/" + decimal(profile.getFixedAllowance())
                + "/" + decimal(profile.getFixedDeduction()) + "/" + profile.getUpdatedAt();
    }

    private String amounts(PayrollAmountsResponse amounts) {
        return decimal(amounts.baseSalaryMonthly()) + "/" + decimal(amounts.allowanceMonthly()) + "/"
                + decimal(amounts.deductionMonthly()) + "/" + decimal(amounts.earnedBaseSalary()) + "/"
                + decimal(amounts.earnedAllowance()) + "/" + decimal(amounts.earnedDeduction()) + "/"
                + decimal(amounts.netSalary());
    }

    private String decimal(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    /** PostgreSQL transaction lock serializes different previews for the same payroll period. */
    private void lockPeriod(short year, short month) {
        long lockKey = (((long) year) << 32) | (month & 0xffffffffL);
        entityManager.createNativeQuery("select pg_advisory_xact_lock(?1)").setParameter(1, lockKey).getSingleResult();
    }
    private record PreviewItem(Employee source, PayrollEmployeeSummaryResponse employee, EmployeeSalaryProfile profile, int paidMinutes, PayrollAmountsResponse amounts) { }
    private record Calculation(short year, short month, boolean closed, int standardDays, int standardMinutesPerDay,
                               List<PreviewItem> items, List<PayrollIssueResponse> issues, String fingerprint,
                               boolean canConfirm, PayrollSummaryResponse summary) {
        PayrollPreviewResponse toPreview(String token, OffsetDateTime expires) {
            PayrollWorkResponse standard = new PayrollWorkResponse(standardDays, standardMinutesPerDay, null, null);
            return new PayrollPreviewResponse(token, expires, year, month, closed, canConfirm ? "READY" : "BLOCKED",
                    canConfirm, standard, summary, items.stream().map(item -> new PayrollPreviewItemResponse(item.employee,
                    new PayrollWorkResponse(standardDays, standardMinutesPerDay, item.paidMinutes,
                            paidWorkingDays(item.paidMinutes, standardMinutesPerDay)), finishAmounts(item.amounts),
                    item.profile.getId())).toList(), issues);
        }
        private static BigDecimal paidWorkingDays(int paidMinutes, int standardMinutesPerDay) {
            return standardMinutesPerDay <= 0 ? null : BigDecimal.valueOf(paidMinutes)
                    .divide(BigDecimal.valueOf(standardMinutesPerDay), 4, RoundingMode.HALF_UP);
        }
        private static PayrollAmountsResponse finishAmounts(PayrollAmountsResponse a) { return new PayrollAmountsResponse(a.baseSalaryMonthly(), a.allowanceMonthly(), a.deductionMonthly(), a.earnedBaseSalary(), a.earnedAllowance(), a.earnedDeduction(), a.earnedBaseSalary().add(a.earnedAllowance()).subtract(a.earnedDeduction())); }
    }
}
