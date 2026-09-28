package com.hrm.backend.service.impl;

import com.hrm.backend.dto.response.*;
import com.hrm.backend.dto.request.CreateEmployeeRequest;
import com.hrm.backend.dto.request.UpdateEmployeeRequest;
import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.AuditLog;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.enums.EmploymentStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.AccountRepository;
import com.hrm.backend.repository.AuditLogRepository;
import com.hrm.backend.service.AccessScopeService;
import com.hrm.backend.service.EmployeeService;
import com.hrm.backend.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;
import java.time.OffsetDateTime;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {
    private final EmployeeRepository employees; private final EmployeeAssignmentRepository assignments; private final AccessScopeService access;
    private final AccountRepository accounts;
    private final AuditLogRepository audits;
    private final EmployeeMapper employeeMapper;
    @Override
    @Transactional
    public EmployeeDto createEmployee(CreateEmployeeRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (employees.existsByEmailIgnoreCase(email)) {
            throw conflict("EMPLOYEE_EMAIL_ALREADY_EXISTS", "Email nhân viên đã tồn tại.");
        }
        if (request.getDateOfBirth() != null && request.getHireDate().isBefore(request.getDateOfBirth())) {
            throw new AuthException("EMPLOYEE_HIRE_DATE_INVALID", "Ngày vào làm phải sau ngày sinh.", 400);
        }
        Employee employee = employees.saveAndFlush(Employee.builder()
                .employeeCode(temporaryEmployeeCode())
                .fullName(request.getFullName().trim())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .email(email)
                .phone(request.getPhone().trim())
                .address(request.getAddress() == null ? null : request.getAddress().trim())
                .hireDate(request.getHireDate())
                .employmentStatus(request.getEmploymentStatus() == null ? EmploymentStatus.WORKING : request.getEmploymentStatus())
                .build());
        employee.setEmployeeCode(generateEmployeeCode(employee.getId()));
        return toDto(employees.save(employee));
    }
    @Override public EmployeeDto getEmployee(Long accountId, Long employeeId) {
        access.requireCanReadEmployee(accountId, employeeId);
        Employee e = employees.findById(employeeId).orElseThrow(() -> new ResourceNotFoundException("EMPLOYEE_NOT_FOUND", "Không tìm thấy nhân viên."));
        EmployeeDto.EmployeeDtoBuilder result = EmployeeDto.builder().id(e.getId()).employeeCode(e.getEmployeeCode()).fullName(e.getFullName()).dateOfBirth(e.getDateOfBirth()).gender(e.getGender() == null ? null : e.getGender().name()).email(e.getEmail()).phone(e.getPhone()).address(e.getAddress()).hireDate(e.getHireDate()).employmentStatus(e.getEmploymentStatus() == null ? null : e.getEmploymentStatus().name());
        assignments.findCurrentAssignmentByEmployeeId(e.getId()).ifPresent(a -> { if (a.getDepartment() != null) result.department(DepartmentDto.builder().id(a.getDepartment().getId()).code(a.getDepartment().getCode()).name(a.getDepartment().getName()).build()); if (a.getPosition() != null) result.position(PositionDto.builder().id(a.getPosition().getId()).code(a.getPosition().getCode()).name(a.getPosition().getName()).build()); if (a.getManagerEmployee() != null) result.manager(EmployeeDto.ManagerDto.builder().id(a.getManagerEmployee().getId()).employeeCode(a.getManagerEmployee().getEmployeeCode()).fullName(a.getManagerEmployee().getFullName()).build()); });
        return result.build();
    }
    @Override
    @Transactional
    public EmployeeDto updateEmployee(Long actorAccountId, Long employeeId, UpdateEmployeeRequest request) {
        if (!request.hasChanges()) {
            throw new AuthException("VALIDATION_ERROR", "Cần gửi ít nhất một trường để cập nhật.", 400);
        }
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("EMPLOYEE_NOT_FOUND", "Không tìm thấy nhân viên."));
        validateTextFields(request);
        if (request.getEmail() != null) {
            String email = employeeMapper.normalizeEmail(request.getEmail());
            if (employees.existsByEmailIgnoreCaseAndIdNot(email, employeeId)) {
                throw conflict("EMPLOYEE_EMAIL_ALREADY_EXISTS", "Email nhân viên đã tồn tại.");
            }
        }
        boolean newBirthDateAfterExistingHireDate = request.getDateOfBirth() != null
                && employee.getHireDate().isBefore(request.getDateOfBirth());
        boolean newHireDateBeforeExistingBirthDate = request.getHireDate() != null
                && employee.getDateOfBirth() != null
                && request.getHireDate().isBefore(employee.getDateOfBirth());
        if (newBirthDateAfterExistingHireDate || newHireDateBeforeExistingBirthDate) {
            throw new AuthException("EMPLOYEE_HIRE_DATE_INVALID", "Ngày vào làm phải sau ngày sinh.", 400);
        }
        String oldData = auditData(employee);
        employeeMapper.updateEntity(request, employee);
        if (employee.getDateOfBirth() != null && employee.getHireDate().isBefore(employee.getDateOfBirth())) {
            throw new AuthException("EMPLOYEE_HIRE_DATE_INVALID", "Ngày vào làm phải sau ngày sinh.", 400);
        }
        Account actor = accounts.findById(actorAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản thực hiện."));
        Employee updatedEmployee = employees.save(employee);
        audits.save(AuditLog.builder()
                .actorAccount(actor)
                .action("EMPLOYEE_UPDATED")
                .entityType("EMPLOYEE")
                .entityId(updatedEmployee.getId())
                .oldData(oldData)
                .newData(auditData(updatedEmployee))
                .occurredAt(OffsetDateTime.now())
                .build());
        return toDto(updatedEmployee);
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
    private void validateTextFields(UpdateEmployeeRequest request) {
        if (request.getFullName() != null && request.getFullName().isBlank()
                || request.getPhone() != null && request.getPhone().isBlank()) {
            throw new AuthException("VALIDATION_ERROR", "Họ tên và số điện thoại không được để trống.", 400);
        }
    }
    private String auditData(Employee employee) {
        return String.format(Locale.ROOT,
                "{\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"hireDate\":\"%s\"}",
                escapeJson(employee.getFullName()), escapeJson(employee.getEmail()),
                escapeJson(employee.getPhone()), employee.getHireDate());
    }
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\b", "\\b").replace("\f", "\\f")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
    private AuthException conflict(String code, String message) { return new AuthException(code, message, 409); }
}
