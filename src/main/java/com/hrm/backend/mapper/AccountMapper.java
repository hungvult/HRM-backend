package com.hrm.backend.mapper;

import com.hrm.backend.dto.request.CreateAccountRequest;
import com.hrm.backend.dto.request.UpdateAccountRequest;
import com.hrm.backend.dto.response.*;
import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.enums.AccountStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class AccountMapper {

    public Account toNewEntity(CreateAccountRequest request, String passwordHash) {
        return Account.builder()
                .username(normalizeUsername(request.getUsername()))
                .email(normalizeEmail(request.getEmail()))
                .passwordHash(passwordHash)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    /** Does not update password, status, employee linkage, login time, or audit timestamps. */
    public void updateEntity(UpdateAccountRequest request, Account account) {
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            account.setUsername(normalizeUsername(request.getUsername()));
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            account.setEmail(normalizeEmail(request.getEmail()));
        }
    }

    public AdminAccountResponse toAdminResponse(Account account, List<String> roles) {
        return AdminAccountResponse.builder()
                .id(account.getId())
                .username(account.getUsername())
                .email(account.getEmail())
                .status(account.getStatus().name())
                .roles(roles)
                .employeeId(account.getEmployee() == null ? null : account.getEmployee().getId())
                .employeeCode(account.getEmployee() == null ? null : account.getEmployee().getEmployeeCode())
                .lastLoginAt(account.getLastLoginAt())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }

    public AccountDetailResponse toDetailResponse(Account account, List<String> roles, EmployeeDto employee) {
        return AccountDetailResponse.builder()
                .id(account.getId())
                .username(account.getUsername())
                .email(account.getEmail())
                .status(account.getStatus().name())
                .roles(roles)
                .employee(employee)
                .build();
    }

    public UpdateAccountResponse toUpdateResponse(Account account) {
        return UpdateAccountResponse.builder()
                .id(account.getId())
                .username(account.getUsername())
                .email(account.getEmail())
                .status(account.getStatus().name())
                .build();
    }

    public UpdateAccountStatusResponse toStatusResponse(Account account) {
        return UpdateAccountStatusResponse.builder()
                .id(account.getId())
                .status(account.getStatus().name())
                .build();
    }

    public ReplaceAccountRolesResponse toRolesResponse(Account account, List<String> roles) {
        return ReplaceAccountRolesResponse.builder().id(account.getId()).roles(roles).build();
    }

    public String normalizeUsername(String value) {
        return value.trim();
    }

    public String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
