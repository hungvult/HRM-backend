package com.hrm.backend.service.impl;

import com.hrm.backend.entity.AccountRole;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.repository.AccountRoleRepository;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.service.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessScopeServiceImpl implements AccessScopeService {
    private final AccountRoleRepository accountRoleRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository assignmentRepository;
    @Override
    public void requireCanReadEmployee(Long accountId, Long employeeId) {
        Set<String> roles = rolesFor(accountId);
        if (roles.contains("ADMIN") || roles.contains("HR")) return;
        Employee actor = requireEmployee(accountId);
        if (roles.contains("EMPLOYEE") && actor.getId().equals(employeeId)) return;
        if (roles.contains("MANAGER") && assignmentRepository.existsByEmployeeIdAndManagerEmployeeIdAndIsCurrentTrue(employeeId, actor.getId())) return;
        throw forbidden("Bạn không có quyền truy cập hồ sơ nhân viên này.");
    }

    @Override
    public void requireCanReadEmployeeAtDate(Long accountId, Long employeeId, LocalDate workDate) {
        Set<String> roles = rolesFor(accountId);
        if (roles.contains("ADMIN") || roles.contains("HR")) return;
        if (!roles.contains("MANAGER")) throw forbidden("Bạn không có quyền truy cập bản ghi công này.");
        Employee manager = requireEmployee(accountId);
        if (assignmentRepository.countManagedEmployeeAtDate(employeeId, manager.getId(), workDate) == 0) {
            throw forbidden("Bạn không có quyền truy cập bản ghi công này.");
        }
    }

    @Override
    public Long managerEmployeeIdForAttendanceScope(Long accountId) {
        Set<String> roles = rolesFor(accountId);
        if (roles.contains("ADMIN") || roles.contains("HR")) return null;
        if (!roles.contains("MANAGER")) throw forbidden("Bạn không có quyền xem bảng công.");
        return requireEmployee(accountId).getId();
    }

    private Set<String> rolesFor(Long accountId) {
        return accountRoleRepository.findByAccountIdWithRole(accountId).stream()
                .map(AccountRole::getRole)
                .map(role -> role.getCode())
                .collect(Collectors.toSet());
    }

    private Employee requireEmployee(Long accountId) {
        return employeeRepository.findByAccountId(accountId)
                .orElseThrow(() -> forbidden("Tài khoản không có hồ sơ nhân viên."));
    }
    private AuthException forbidden(String message) { return new AuthException("AUTH_FORBIDDEN_SCOPE", message, 403); }
}
