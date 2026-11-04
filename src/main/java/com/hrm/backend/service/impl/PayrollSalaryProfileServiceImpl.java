package com.hrm.backend.service.impl;

import com.hrm.backend.dto.request.CreateSalaryProfileRequest;
import com.hrm.backend.dto.request.UpdateSalaryProfileRequest;
import com.hrm.backend.dto.response.*;
import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.Department;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.EmployeeAssignment;
import com.hrm.backend.entity.EmployeeSalaryProfile;
import com.hrm.backend.entity.enums.SalaryProfileState;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.dto.response.ErrorResponse;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import com.hrm.backend.repository.AccountRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.EmployeeSalaryProfileRepository;
import com.hrm.backend.repository.PayrollItemRepository;
import com.hrm.backend.service.PayrollSalaryProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollSalaryProfileServiceImpl implements PayrollSalaryProfileService {
    private static final ZoneId BUSINESS_TIMEZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal MAX_VND_AMOUNT = new BigDecimal("999999999999");

    private final AccountRepository accounts;
    private final EmployeeRepository employees;
    private final EmployeeAssignmentRepository assignments;
    private final EmployeeSalaryProfileRepository salaryProfiles;
    private final PayrollItemRepository payrollItems;

    @Override
    public PagedResponse<SalaryProfileEmployeeListItemResponse> searchEmployees(
            String q, Long departmentId, SalaryProfileState profileState, int page, int size) {
        validateSearch(q, departmentId, page, size);
        String keyword = q == null ? "" : q.trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by("employeeCode").ascending().and(Sort.by("id").ascending()));
        LocalDate today = LocalDate.now(BUSINESS_TIMEZONE);
        Page<Employee> employeePage = employees.searchPayrollEmployees(
                keyword, departmentId, profileState == null ? null : profileState.name(), today, pageable);

        List<SalaryProfileEmployeeListItemResponse> content = employeePage.getContent().stream()
                .map(employee -> toListItem(employee, today))
                .toList();

        return PagedResponse.<SalaryProfileEmployeeListItemResponse>builder()
                .content(content)
                .page(employeePage.getNumber())
                .size(employeePage.getSize())
                .totalElements(employeePage.getTotalElements())
                .totalPages(employeePage.getTotalPages())
                .hasNext(employeePage.hasNext())
                .build();
    }

    @Override
    public SalaryProfileHistoryResponse getEmployeeSalaryProfileHistory(Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            throw validation("employeeId phải là số nguyên dương.");
        }
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("EMPLOYEE_NOT_FOUND", "Không tìm thấy nhân viên."));
        return SalaryProfileHistoryResponse.builder()
                .employee(toEmployeeSummary(employee))
                .profiles(salaryProfiles.findByEmployeeIdOrderByEffectiveFromDescIdDesc(employeeId).stream()
                        .map(this::toHistoryItem)
                        .toList())
                .build();
    }

    @Override
    @Transactional
    public SalaryProfileHistoryItemResponse createSalaryProfile(Long actorAccountId, CreateSalaryProfileRequest request) {
        if (request == null || request.getEmployeeId() == null || request.getEmployeeId() <= 0) {
            throw validation("employeeId phải là số nguyên dương.");
        }
        validateProfileInput(request.getEffectiveFrom(), request.getBaseSalary(), request.getAllowance(), request.getDeduction());

        Employee employee = lockEmployee(request.getEmployeeId());
        List<EmployeeSalaryProfile> profiles = profilesFor(employee.getId());
        LocalDate effectiveFrom = request.getEffectiveFrom();
        if (profiles.stream().anyMatch(profile -> profile.getEffectiveFrom().equals(effectiveFrom))) {
            throw conflict("SALARY_PROFILE_OVERLAP", "Đã có mốc lương bắt đầu trong tháng này.");
        }

        EmployeeSalaryProfile previous = profiles.stream()
                .filter(profile -> profile.getEffectiveFrom().isBefore(effectiveFrom))
                .max(Comparator.comparing(EmployeeSalaryProfile::getEffectiveFrom))
                .orElse(null);
        EmployeeSalaryProfile next = profiles.stream()
                .filter(profile -> profile.getEffectiveFrom().isAfter(effectiveFrom))
                .min(Comparator.comparing(EmployeeSalaryProfile::getEffectiveFrom))
                .orElse(null);

        LocalDate previousEnd = effectiveFrom.minusDays(1);
        if (previous != null && !Objects.equals(previous.getEffectiveTo(), previousEnd)
                && !canSetEffectiveTo(previous, previousEnd)) {
            throw locked();
        }

        // The overlap trigger is immediate. Persist the smaller previous range before inserting
        // the successor so PostgreSQL never observes two overlapping rows in this transaction.
        if (previous != null) {
            previous.setEffectiveTo(previousEnd);
            salaryProfiles.saveAndFlush(previous);
        }
        EmployeeSalaryProfile profile = EmployeeSalaryProfile.builder()
                .employee(employee)
                .effectiveFrom(effectiveFrom)
                .effectiveTo(next == null ? null : next.getEffectiveFrom().minusDays(1))
                .baseSalary(request.getBaseSalary())
                .fixedAllowance(request.getAllowance())
                .fixedDeduction(request.getDeduction())
                .createdByAccount(accounts.getReferenceById(actorAccountId))
                .build();
        return toHistoryItem(salaryProfiles.save(profile));
    }

    @Override
    @Transactional
    public SalaryProfileHistoryItemResponse updateSalaryProfile(Long profileId, UpdateSalaryProfileRequest request) {
        if (profileId == null || profileId <= 0) throw validation("profileId phải là số nguyên dương.");
        validateProfileInput(request == null ? null : request.getEffectiveFrom(), request == null ? null : request.getBaseSalary(),
                request == null ? null : request.getAllowance(), request == null ? null : request.getDeduction());

        EmployeeSalaryProfile beforeLock = salaryProfiles.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("SALARY_PROFILE_NOT_FOUND", "Không tìm thấy mốc lương."));
        Employee employee = lockEmployee(beforeLock.getEmployee().getId());
        EmployeeSalaryProfile target = salaryProfiles.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("SALARY_PROFILE_NOT_FOUND", "Không tìm thấy mốc lương."));
        Long targetId = target.getId();
        if (payrollItems.existsBySalaryProfileId(target.getId())) throw locked();

        List<EmployeeSalaryProfile> profiles = profilesFor(employee.getId());
        if (profiles.stream().anyMatch(profile -> !profile.getId().equals(targetId)
                && profile.getEffectiveFrom().equals(request.getEffectiveFrom()))) {
            throw conflict("SALARY_PROFILE_OVERLAP", "Đã có mốc lương bắt đầu trong tháng này.");
        }

        List<EmployeeSalaryProfile> reordered = new ArrayList<>(profiles);
        reordered.sort(Comparator.comparing(profile -> profile.getId().equals(targetId)
                ? request.getEffectiveFrom() : profile.getEffectiveFrom()));
        Map<Long, LocalDate> expectedEnds = new HashMap<>();
        for (int i = 0; i < reordered.size(); i++) {
            LocalDate end = i + 1 < reordered.size() ? effectiveStartOf(reordered.get(i + 1), target, request).minusDays(1) : null;
            expectedEnds.put(reordered.get(i).getId(), end);
        }
        for (EmployeeSalaryProfile profile : profiles) {
            LocalDate expectedEnd = expectedEnds.get(profile.getId());
            if (!profile.getId().equals(target.getId()) && !Objects.equals(profile.getEffectiveTo(), expectedEnd)
                    && !canSetEffectiveTo(profile, expectedEnd)) {
                throw locked();
            }
        }

        // First close intervals. This can only create a gap, never an overlap.
        for (EmployeeSalaryProfile profile : profiles) {
            if (!profile.getId().equals(target.getId())
                    && isEndShortened(profile.getEffectiveTo(), expectedEnds.get(profile.getId()))) {
                profile.setEffectiveTo(expectedEnds.get(profile.getId()));
                salaryProfiles.saveAndFlush(profile);
            }
        }

        // Then move the target. A predecessor/successor that could overlap it was closed above.
        target.setEffectiveFrom(request.getEffectiveFrom());
        target.setEffectiveTo(expectedEnds.get(target.getId()));
        target.setBaseSalary(request.getBaseSalary());
        target.setFixedAllowance(request.getAllowance());
        target.setFixedDeduction(request.getDeduction());
        target = salaryProfiles.saveAndFlush(target);

        // Finally extend intervals only after the target has moved out of their way.
        for (EmployeeSalaryProfile profile : profiles) {
            if (!profile.getId().equals(target.getId())
                    && isEndExtended(profile.getEffectiveTo(), expectedEnds.get(profile.getId()))) {
                profile.setEffectiveTo(expectedEnds.get(profile.getId()));
                salaryProfiles.saveAndFlush(profile);
            }
        }
        return toHistoryItem(target);
    }

    private SalaryProfileEmployeeListItemResponse toListItem(Employee employee, LocalDate today) {
        List<EmployeeSalaryProfile> profiles = salaryProfiles.findByEmployeeIdOrderByEffectiveFromDescIdDesc(employee.getId());
        SalaryProfileState state = profileStateFor(profiles, today);
        EmployeeSalaryProfile current = profiles.stream().filter(profile -> isCurrent(profile, today)).findFirst().orElse(null);
        return SalaryProfileEmployeeListItemResponse.builder()
                .employee(toEmployeeSummary(employee))
                .profileState(state)
                .currentProfile(current == null ? null : toCurrentProfile(current))
                .build();
    }

    private SalaryProfileState profileStateFor(List<EmployeeSalaryProfile> profiles, LocalDate today) {
        if (profiles.isEmpty()) return SalaryProfileState.NONE;
        if (profiles.stream().anyMatch(profile -> isCurrent(profile, today))) return SalaryProfileState.CURRENT;
        if (profiles.stream().anyMatch(profile -> profile.getEffectiveFrom().isAfter(today))) return SalaryProfileState.FUTURE;
        return SalaryProfileState.HISTORICAL;
    }

    private boolean isCurrent(EmployeeSalaryProfile profile, LocalDate today) {
        return !profile.getEffectiveFrom().isAfter(today)
                && (profile.getEffectiveTo() == null || !profile.getEffectiveTo().isBefore(today));
    }

    private PayrollEmployeeSummaryResponse toEmployeeSummary(Employee employee) {
        EmployeeAssignment assignment = assignments.findCurrentAssignmentByEmployeeId(employee.getId()).orElse(null);
        Department department = assignment == null ? null : assignment.getDepartment();
        return PayrollEmployeeSummaryResponse.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .department(department == null ? null : PayrollDepartmentSnapshotResponse.builder()
                        .id(department.getId()).code(department.getCode()).name(department.getName()).build())
                .build();
    }

    private CurrentSalaryProfileResponse toCurrentProfile(EmployeeSalaryProfile profile) {
        return CurrentSalaryProfileResponse.builder()
                .id(profile.getId()).effectiveFrom(profile.getEffectiveFrom()).effectiveTo(profile.getEffectiveTo())
                .baseSalary(profile.getBaseSalary()).allowance(profile.getFixedAllowance())
                .deduction(profile.getFixedDeduction()).build();
    }

    private SalaryProfileHistoryItemResponse toHistoryItem(EmployeeSalaryProfile profile) {
        return SalaryProfileHistoryItemResponse.builder()
                .id(profile.getId()).effectiveFrom(profile.getEffectiveFrom()).effectiveTo(profile.getEffectiveTo())
                .baseSalary(profile.getBaseSalary()).allowance(profile.getFixedAllowance())
                .deduction(profile.getFixedDeduction())
                .editable(!payrollItems.existsBySalaryProfileId(profile.getId()))
                .createdAt(profile.getCreatedAt()).updatedAt(profile.getUpdatedAt()).build();
    }

    private Employee lockEmployee(Long employeeId) {
        return employees.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("EMPLOYEE_NOT_FOUND", "Không tìm thấy nhân viên."));
    }

    private List<EmployeeSalaryProfile> profilesFor(Long employeeId) {
        return salaryProfiles.findByEmployeeIdOrderByEffectiveFromDescIdDesc(employeeId);
    }

    private LocalDate effectiveStartOf(EmployeeSalaryProfile profile, EmployeeSalaryProfile target,
                                       UpdateSalaryProfileRequest request) {
        return profile.getId().equals(target.getId()) ? request.getEffectiveFrom() : profile.getEffectiveFrom();
    }

    private void validateProfileInput(LocalDate effectiveFrom, BigDecimal baseSalary,
                                      BigDecimal allowance, BigDecimal deduction) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        if (effectiveFrom == null || effectiveFrom.getDayOfMonth() != 1) {
            errors.add(fieldError("effectiveFrom", "Ngày hiệu lực phải là ngày đầu tháng."));
        }
        if (!isValidVnd(baseSalary, true)) {
            errors.add(fieldError("baseSalary", "Lương cơ bản phải lớn hơn 0, là số VND nguyên và không vượt giới hạn."));
        }
        if (!isValidVnd(allowance, false)) {
            errors.add(fieldError("allowance", "Phụ cấp không âm, là số VND nguyên và không vượt giới hạn."));
        }
        if (!isValidVnd(deduction, false)) {
            errors.add(fieldError("deduction", "Khấu trừ không âm, là số VND nguyên và không vượt giới hạn."));
        }
        if (!errors.isEmpty()) {
            String code = errors.size() == 1 && "effectiveFrom".equals(errors.getFirst().getField())
                    ? "SALARY_PROFILE_EFFECTIVE_DATE_INVALID"
                    : errors.stream().allMatch(error -> !"effectiveFrom".equals(error.getField()))
                    ? "SALARY_AMOUNT_OUT_OF_RANGE"
                    : "VALIDATION_ERROR";
            throw unprocessable(code, "Dữ liệu mốc lương không hợp lệ.", errors);
        }
    }

    private boolean isValidVnd(BigDecimal value, boolean mustBePositive) {
        if (value == null || value.compareTo(MAX_VND_AMOUNT) > 0) return false;
        if (mustBePositive ? value.compareTo(BigDecimal.ZERO) <= 0 : value.compareTo(BigDecimal.ZERO) < 0) return false;
        return value.stripTrailingZeros().scale() <= 0;
    }

    private boolean canSetEffectiveTo(EmployeeSalaryProfile profile, LocalDate proposedEffectiveTo) {
        return payrollItems.findFirstBySalaryProfileIdOrderByPayrollRunPayrollYearDescPayrollRunPayrollMonthDesc(profile.getId())
                .map(item -> {
                    LocalDate latestPayrollEnd = YearMonth.of(
                            item.getPayrollRun().getPayrollYear(), item.getPayrollRun().getPayrollMonth()).atEndOfMonth();
                    return proposedEffectiveTo == null || !proposedEffectiveTo.isBefore(latestPayrollEnd);
                })
                .orElse(true);
    }

    private boolean isEndShortened(LocalDate currentEnd, LocalDate expectedEnd) {
        if (Objects.equals(currentEnd, expectedEnd) || expectedEnd == null) return false;
        return currentEnd == null || expectedEnd.isBefore(currentEnd);
    }

    private boolean isEndExtended(LocalDate currentEnd, LocalDate expectedEnd) {
        if (Objects.equals(currentEnd, expectedEnd) || currentEnd == null) return false;
        return expectedEnd == null || expectedEnd.isAfter(currentEnd);
    }

    private ErrorResponse.FieldError fieldError(String field, String message) {
        return ErrorResponse.FieldError.builder().field(field).message(message).build();
    }

    private void validateSearch(String q, Long departmentId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw validation("page phải từ 0 và size phải trong khoảng 1 đến 100.");
        if (departmentId != null && departmentId <= 0) throw validation("departmentId phải là số nguyên dương.");
        if (q != null && q.trim().length() > 100) throw validation("q không được vượt quá 100 ký tự.");
    }

    private AuthException validation(String message) {
        return new AuthException("VALIDATION_ERROR", message, 400);
    }

    private AuthException unprocessable(String code, String message, List<ErrorResponse.FieldError> errors) {
        return new AuthException(code, message, 422, errors);
    }

    private AuthException conflict(String code, String message) {
        return new AuthException(code, message, 409);
    }

    private AuthException locked() {
        return conflict("SALARY_PROFILE_LOCKED", "Mốc lương đã được dùng bởi bảng lương và không thể thay đổi.");
    }
}
