package com.hrm.backend.service.impl;

import com.hrm.backend.dto.request.*;
import com.hrm.backend.dto.response.AdminAccountResponse;
import com.hrm.backend.entity.*;
import com.hrm.backend.entity.enums.AccountStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.*;
import com.hrm.backend.service.AccountAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountAdminServiceImpl implements AccountAdminService {
    private final AccountRepository accounts;
    private final EmployeeRepository employees;
    private final RoleRepository roles;
    private final AccountRoleRepository accountRoles;
    private final AuthSessionRepository sessions;
    private final AuditLogRepository audits;
    private final PasswordEncoder encoder;
    @Override
    public AdminAccountResponse create(Long actorId, CreateAccountRequest req) {
        String username = req.getUsername().trim();
        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);
        if (accounts.existsByUsernameIgnoreCase(username)) throw conflict("USERNAME_ALREADY_EXISTS", "Tên đăng nhập đã tồn tại.");
        if (accounts.existsByEmailIgnoreCase(email)) throw conflict("EMAIL_ALREADY_EXISTS", "Email đã tồn tại.");

        Employee employee = employees.findById(req.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("EMPLOYEE_NOT_FOUND", "Không tìm thấy nhân viên."));
        if (employee.getAccount() != null) {
            throw conflict("EMPLOYEE_ALREADY_HAS_ACCOUNT", "Nhân viên này đã được liên kết với một tài khoản.");
        }

        Account account = accounts.save(Account.builder().username(username).email(email).passwordHash(encoder.encode(req.getPassword())).status(AccountStatus.ACTIVE).passwordChangedAt(OffsetDateTime.now()).build());
        employee.setAccount(account);
        account.setEmployee(employee);
        employees.save(employee);
        replaceRolesInternal(actorId, account, req.getRoles()); audit(actorId, "ACCOUNT_CREATE", "accounts", account.getId(), "{\"username\":\"" + username + "\"}");
        return map(account);
    }
    @Override
    @Transactional(readOnly = true)
    public Page<AdminAccountResponse> search(String keyword, AccountStatus status, String role, Pageable pageable) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? "" : keyword.trim();
        String normalizedRole = role == null || role.isBlank() ? "" : role.trim().toUpperCase(Locale.ROOT);
        return accounts.search(normalizedKeyword, status, normalizedRole, safePageable(pageable)).map(this::map);
    }
    @Override
    @Transactional(readOnly = true) public AdminAccountResponse get(Long accountId) { return map(account(accountId)); }
    @Override
    public AdminAccountResponse update(Long actorId, Long accountId, UpdateAccountRequest req) {
        Account target = account(accountId);
        if (req.getUsername() != null && !req.getUsername().isBlank()) { String username = req.getUsername().trim();
            if (!username.equalsIgnoreCase(target.getUsername()) && accounts.existsByUsernameIgnoreCase(username)) throw conflict("USERNAME_ALREADY_EXISTS", "Tên đăng nhập đã tồn tại."); target.setUsername(username); }
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            String email = req.getEmail().trim().toLowerCase(Locale.ROOT);
            if (!email.equalsIgnoreCase(target.getEmail()) && accounts.existsByEmailIgnoreCase(email)) throw conflict("EMAIL_ALREADY_EXISTS", "Email đã tồn tại."); target.setEmail(email); }
        audit(actorId, "ACCOUNT_UPDATE", "accounts", target.getId(), "{\"username\":\"" + target.getUsername() + "\"}");
        return map(target);
    }
    @Override
    public AdminAccountResponse updateStatus(Long actorId, Long accountId, UpdateAccountStatusRequest req) {
        Account target = account(accountId);
        if (actorId.equals(accountId) && req.getStatus() != AccountStatus.ACTIVE)
            throw conflict("CANNOT_CHANGE_OWN_STATUS", "Không thể khóa hoặc vô hiệu hóa tài khoản của chính mình.");
        target.setStatus(req.getStatus());
        if (req.getStatus() != AccountStatus.ACTIVE)
            sessions.revokeActiveByAccountId(target.getId(), OffsetDateTime.now(), "ACCOUNT_" + req.getStatus());
        audit(actorId, "ACCOUNT_STATUS_UPDATE", "accounts", target.getId(), "{\"status\":\"" + req.getStatus() + "\"}");
        return map(target);
    }
    @Override
    public AdminAccountResponse replaceRoles(Long actorId, Long accountId, ReplaceAccountRolesRequest req) {
        Account target = account(accountId);
        Set<String> codes = normalizedRoles(req.getRoles());
        Set<String> current = roleCodes(target.getId());
        if (actorId.equals(accountId) && !codes.contains("ADMIN"))
            throw conflict("CANNOT_REMOVE_OWN_ADMIN_ROLE", "Không thể tự gỡ role ADMIN.");
        if (current.contains("ADMIN") && !codes.contains("ADMIN") && accountRoles.countByRoleCode("ADMIN") <= 1)
            throw conflict("LAST_ADMIN_ROLE", "Hệ thống phải còn ít nhất một ADMIN.");
        replaceRolesInternal(actorId, target, codes);
        sessions.revokeActiveByAccountId(target.getId(), OffsetDateTime.now(), "ROLE_CHANGED");
        audit(actorId, "ACCOUNT_ROLE_REPLACE", "accounts", target.getId(), "{\"roles\":\"" + String.join(",", codes) + "\"}");
        return map(target);
    }
    private void replaceRolesInternal(Long actorId, Account target, Collection<String> requested) {
        Set<String> codes = normalizedRoles(requested);
        List<Role> selected = roles.findByCodeIn(codes);
        if (selected.size() != codes.size()) throw conflict("ROLE_NOT_FOUND", "Có role không tồn tại.");
        accountRoles.deleteAll(accountRoles.findByAccountIdWithRole(target.getId()));
        accountRoles.flush(); Account actor = account(actorId);
        for (Role role : selected)
            accountRoles.save(AccountRole.builder().id(new AccountRole.AccountRoleId(target.getId(), role.getId())).account(target).role(role).assignedByAccount(actor).build());
    }
    private Account account(Long id) {
        return accounts.findByIdWithEmployee(id).orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản."));
    }
    private Set<String> normalizedRoles(Collection<String> input) {
        return input.stream().map(s -> s.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toCollection(LinkedHashSet::new));
    }
    private Pageable safePageable(Pageable pageable) {
        Set<String> allowed = Set.of("id", "username", "email", "status", "createdAt", "lastLoginAt");
        List<Sort.Order> orders = pageable.getSort().stream().filter(order -> allowed.contains(order.getProperty())).toList();
        Sort sort = orders.isEmpty() ? Sort.by(Sort.Direction.ASC, "id") : Sort.by(orders);
        return PageRequest.of(Math.max(0, pageable.getPageNumber()), Math.min(Math.max(1, pageable.getPageSize()), 100), sort);
    }
    private Set<String> roleCodes(Long accountId) {
        return accountRoles.findByAccountIdWithRole(accountId).stream().map(AccountRole::getRole).map(Role::getCode).collect(Collectors.toSet());
    }
    private AdminAccountResponse map(Account a) {
        Employee e = a.getEmployee();
        return AdminAccountResponse.builder()
                .id(a.getId())
                .username(a.getUsername())
                .email(a.getEmail())
                .status(a.getStatus().name())
                .roles(roleCodes(a.getId()).stream().sorted().toList())
                .employeeId(e == null ? null : e.getId()).employeeCode(e == null ? null : e.getEmployeeCode()).lastLoginAt(a.getLastLoginAt()).createdAt(a.getCreatedAt()).updatedAt(a.getUpdatedAt()).build();
    }
    private void audit(Long actorId, String action, String type, Long id, String newData) {
        audits.save(AuditLog.builder().actorAccount(account(actorId)).action(action).entityType(type).entityId(id).newData(newData).occurredAt(OffsetDateTime.now()).build());
    }
    private AuthException conflict(String code, String message) {
        return new AuthException(code, message, 409);
    }
}
