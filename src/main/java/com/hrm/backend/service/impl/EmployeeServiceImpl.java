package com.hrm.backend.service.impl;

import com.hrm.backend.dto.response.*;
import com.hrm.backend.dto.request.CreateEmployeeRequest;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.enums.EmploymentStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.service.AccessScopeService;
import com.hrm.backend.service.EmployeeService;
import com.hrm.backend.mapper.EmployeeListMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {
    private final EmployeeRepository employees;
    private final EmployeeAssignmentRepository assignments; private final AccessScopeService access;
    private final EmployeeListMapper employeeListMapper;
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
    private AuthException conflict(String code, String message) { return new AuthException(code, message, 409); }
}
