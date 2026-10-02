package com.hrm.backend.service.impl;

import com.hrm.backend.dto.response.*;
import com.hrm.backend.dto.request.CreateEmployeeRequest;
import com.hrm.backend.dto.request.UpdateEmployeeStatusRequest;
import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.AuditLog;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.EmployeeStatusHistory;
import com.hrm.backend.entity.enums.EmploymentStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.EmployeeStatusHistoryRepository;
import com.hrm.backend.repository.AccountRepository;
import com.hrm.backend.repository.AuditLogRepository;
import com.hrm.backend.service.AccessScopeService;
import com.hrm.backend.service.EmployeeService;
import com.hrm.backend.mapper.EmployeeListMapper;
import com.hrm.backend.mapper.EmployeeMapper;
import com.hrm.backend.mapper.EmployeeStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {
    private final EmployeeRepository employees;
    private final EmployeeAssignmentRepository assignments; 
    private final EmployeeListMapper employeeListMapper;
    private final AccessScopeService access;
    private final AccountRepository accounts;
    private final EmployeeStatusHistoryRepository statusHistories;
    private final AuditLogRepository audits;
    private final EmployeeMapper employeeMapper;
    private final EmployeeStatusMapper employeeStatusMapper;
    @Override
    @Transactional
    public EmployeeDto createEmployee(Long actorAccountId, CreateEmployeeRequest request) {
        String email = employeeMapper.normalizeEmail(request.getEmail());
        if (employees.existsByEmailIgnoreCase(email)) {
            throw conflict("EMPLOYEE_EMAIL_ALREADY_EXISTS", "Email nhân viên đã tồn tại.");
        }
        if (request.getDateOfBirth() != null && request.getHireDate().isBefore(request.getDateOfBirth())) {
            throw new AuthException("EMPLOYEE_HIRE_DATE_INVALID", "Ngày vào làm phải sau ngày sinh.", 400);
        }
        Account actor = accounts.findById(actorAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản thực hiện."));
        Employee employee = employeeMapper.toNewEntity(request);
        employee.setEmployeeCode(temporaryEmployeeCode());
        employee.setEmploymentStatus(EmploymentStatus.WORKING);
        employee = employees.saveAndFlush(employee);
        employee.setEmployeeCode(generateEmployeeCode(employee.getId()));
        employee = employees.save(employee);

        statusHistories.save(EmployeeStatusHistory.builder()
                .employee(employee)
                .newStatus(EmploymentStatus.WORKING)
                .changedByAccount(actor)
                .build());
        audits.save(AuditLog.builder()
                .actorAccount(actor)
                .action("EMPLOYEE_CREATED")
                .entityType("EMPLOYEE")
                .entityId(employee.getId())
                .newData(employeeCreatedAuditData(employee))
                .occurredAt(OffsetDateTime.now())
                .build());
        return employeeMapper.toDto(employee);
    }
    @Override public EmployeeDto getEmployee(Long accountId, Long employeeId) {
        access.requireCanReadEmployee(accountId, employeeId);
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("EMPLOYEE_NOT_FOUND", "Không tìm thấy nhân viên."));
        return employeeMapper.toDetailResponse(employee,
                assignments.findCurrentAssignmentByEmployeeId(employee.getId()).orElse(null));
    }
    @Override
    public PagedResponse<EmployeeListItemResponse> searchEmployees(String q, EmploymentStatus employmentStatus,
                                                                     Long departmentId, Long positionId, int page,
                                                                     int size, String sortBy, String sortDirection) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AuthException("VALIDATION_ERROR", "page phải từ 0 và size phải trong khoảng 1 đến 100.", 400);
        }
        Set<String> allowedSortFields = Set.of("employeeCode", "fullName", "hireDate", "createdAt");
        if (!allowedSortFields.contains(sortBy)) {
            throw new AuthException("VALIDATION_ERROR", "sortBy không hợp lệ.", 400);
        }
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(sortDirection);
        } catch (IllegalArgumentException exception) {
            throw new AuthException("VALIDATION_ERROR", "sortDirection chỉ nhận asc hoặc desc.", 400);
        }
        String keyword = q == null ? "" : q.trim();
        Page<Employee> employeesPage = employees.searchEmployees(keyword, employmentStatus, departmentId, positionId,
                PageRequest.of(page, size, Sort.by(direction, sortBy)));
        return PagedResponse.<EmployeeListItemResponse>builder()
                .content(employeesPage.getContent().stream()
                        .map(employee -> employeeListMapper.toResponse(employee,
                                assignments.findCurrentAssignmentByEmployeeId(employee.getId()).orElse(null)))
                        .toList())
                .page(employeesPage.getNumber())
                .size(employeesPage.getSize())
                .totalElements(employeesPage.getTotalElements())
                .totalPages(employeesPage.getTotalPages())
                .hasNext(employeesPage.hasNext())
                .build();
    }
    @Override
    @Transactional
    public UpdateEmployeeStatusResponse updateEmployeeStatus(Long actorAccountId, Long employeeId,
                                                              UpdateEmployeeStatusRequest request) {
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("EMPLOYEE_NOT_FOUND", "Không tìm thấy nhân viên."));
        EmploymentStatus oldStatus = employee.getEmploymentStatus();
        EmploymentStatus newStatus = request.getEmploymentStatus();
        if (oldStatus == newStatus) {
            throw new AuthException("VALIDATION_ERROR", "Trạng thái mới phải khác trạng thái hiện tại.", 400);
        }
        Account actor = accounts.findById(actorAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản thực hiện."));
        String reason = request.getReason() == null ? null : request.getReason().trim();
        employee.setEmploymentStatus(newStatus);
        EmployeeStatusHistory history = statusHistories.save(EmployeeStatusHistory.builder()
                .employee(employee)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedByAccount(actor)
                .reason(reason)
                .build());
        audits.save(AuditLog.builder()
                .actorAccount(actor)
                .action("EMPLOYEE_STATUS_UPDATED")
                .entityType("EMPLOYEE")
                .entityId(employee.getId())
                .oldData(statusAuditData(oldStatus, null))
                .newData(statusAuditData(newStatus, reason))
                .occurredAt(OffsetDateTime.now())
                .build());
        return employeeStatusMapper.toResponse(employee, history);
    }
    private EmployeeDto toDto(Employee e) {
        return EmployeeDto.builder().id(e.getId()).employeeCode(e.getEmployeeCode()).fullName(e.getFullName())
                .dateOfBirth(e.getDateOfBirth()).gender(e.getGender() == null ? null : e.getGender().name())
                .email(e.getEmail()).phone(e.getPhone()).address(e.getAddress()).hireDate(e.getHireDate())
                .employmentStatus(e.getEmploymentStatus() == null ? null : e.getEmploymentStatus().name()).build();
    }
    private String temporaryEmployeeCode() {
        return "TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT);
    }
    private String generateEmployeeCode(Long employeeId) {
        return String.format(Locale.ROOT, "NV%06d", employeeId);
    }
    private String statusAuditData(EmploymentStatus status, String reason) {
        String reasonValue = reason == null ? "null" : "\"" + escapeJson(reason) + "\"";
        return String.format(Locale.ROOT, "{\"employmentStatus\":\"%s\",\"reason\":%s}", status.name(), reasonValue);
    }
    private String employeeCreatedAuditData(Employee employee) {
        return String.format(Locale.ROOT,
                "{\"employeeCode\":\"%s\",\"email\":\"%s\",\"employmentStatus\":\"%s\"}",
                escapeJson(employee.getEmployeeCode()),
                escapeJson(employee.getEmail()),
                employee.getEmploymentStatus().name());
    }
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\b", "\\b").replace("\f", "\\f")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
    private AuthException conflict(String code, String message) { return new AuthException(code, message, 409); }
}
