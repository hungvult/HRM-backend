package com.hrm.backend.service;

import com.hrm.backend.dto.request.*;
import com.hrm.backend.dto.response.AccountDetailResponse;
import com.hrm.backend.dto.response.AdminAccountResponse;
import com.hrm.backend.dto.response.ReplaceAccountRolesResponse;
import com.hrm.backend.dto.response.UpdateAccountResponse;
import com.hrm.backend.dto.response.UpdateAccountStatusResponse;
import com.hrm.backend.entity.enums.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AccountAdminService {
    AdminAccountResponse create(Long actorId, CreateAccountRequest request);
    Page<AdminAccountResponse> search(String keyword, AccountStatus status, String role, Pageable pageable);
    AccountDetailResponse getDetail(Long accountId);
    UpdateAccountResponse update(Long actorId, Long accountId, UpdateAccountRequest request);
    UpdateAccountStatusResponse updateStatus(Long actorId, Long accountId, UpdateAccountStatusRequest request);
    ReplaceAccountRolesResponse replaceRoles(Long actorId, Long accountId, ReplaceAccountRolesRequest request);
}
