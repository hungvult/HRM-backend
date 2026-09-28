package com.hrm.backend.service.impl;

import com.hrm.backend.dto.response.*;
import com.hrm.backend.dto.request.CreateEmployeeRequest;
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
import com.hrm.backend.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {
    private final EmployeeRepository employees;
    private final EmployeeAssignmentRepository assignments;
    private final AccessScopeService access;
    private final AccountRepository accounts;
    private final EmployeeStatusHistoryRepository statusHistories;
    private final AuditLogRepository audits;
    private final EmployeeMapper employeeMapper;
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
        Employee e = employees.findById(employeeId).orElseThrow(() -> new ResourceNotFoundException("EMPLOYEE_NOT_FOUND", "Không tìm thấy nhân viên."));
        EmployeeDto.EmployeeDtoBuilder result = EmployeeDto.builder().id(e.getId()).employeeCode(e.getEmployeeCode()).fullName(e.getFullName()).dateOfBirth(e.getDateOfBirth()).gender(e.getGender() == null ? null : e.getGender().name()).email(e.getEmail()).phone(e.getPhone()).address(e.getAddress()).hireDate(e.getHireDate()).employmentStatus(e.getEmploymentStatus() == null ? null : e.getEmploymentStatus().name());
        assignments.findCurrentAssignmentByEmployeeId(e.getId()).ifPresent(a -> { if (a.getDepartment() != null) result.department(DepartmentDto.builder().id(a.getDepartment().getId()).code(a.getDepartment().getCode()).name(a.getDepartment().getName()).build()); if (a.getPosition() != null) result.position(PositionDto.builder().id(a.getPosition().getId()).code(a.getPosition().getCode()).name(a.getPosition().getName()).build()); if (a.getManagerEmployee() != null) result.manager(EmployeeDto.ManagerDto.builder().id(a.getManagerEmployee().getId()).employeeCode(a.getManagerEmployee().getEmployeeCode()).fullName(a.getManagerEmployee().getFullName()).build()); });
        return result.build();
    }
    private String temporaryEmployeeCode() {
        return "TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT);
    }
    private String generateEmployeeCode(Long employeeId) {
        return String.format(Locale.ROOT, "NV%06d", employeeId);
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
