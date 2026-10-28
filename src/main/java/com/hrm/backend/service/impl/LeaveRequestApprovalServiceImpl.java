package com.hrm.backend.service.impl;

import com.hrm.backend.dto.response.LeaveRequestApprovalResponse;
import com.hrm.backend.entity.AccountRole;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.LeaveBalance;
import com.hrm.backend.entity.LeaveRequest;
import com.hrm.backend.entity.LeaveRequestStatusHistory;
import com.hrm.backend.entity.enums.LeaveRequestStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.AccountRoleRepository;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.LeaveBalanceRepository;
import com.hrm.backend.repository.LeaveRequestRepository;
import com.hrm.backend.repository.LeaveRequestStatusHistoryRepository;
import com.hrm.backend.service.LeaveRequestApprovalService;
import com.hrm.backend.service.LeaveRequestNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveRequestApprovalServiceImpl implements LeaveRequestApprovalService {
    private final LeaveRequestRepository leaveRequests;
    private final LeaveBalanceRepository leaveBalances;
    private final LeaveRequestStatusHistoryRepository statusHistories;
    private final EmployeeRepository employees;
    private final EmployeeAssignmentRepository assignments;
    private final AccountRoleRepository accountRoles;
    private final LeaveRequestNotificationService notificationService;

    @Override
    @Transactional
    public LeaveRequestApprovalResponse approve(Long accountId, Long leaveRequestId) {
        LeaveRequest request = leaveRequests.findWithEmployeeAndLeaveTypeByIdForUpdate(leaveRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("LEAVE_REQUEST_NOT_FOUND", "Không tìm thấy đơn nghỉ phép."));
        Employee reviewer = employees.findByAccountId(accountId)
                .orElseThrow(() -> forbidden("Tài khoản chưa có hồ sơ nhân viên."));

        requireApprovalPermission(accountId, reviewer, request);
        if (request.getStatus() != LeaveRequestStatus.PENDING) {
            throw conflict("LEAVE_REQUEST_NOT_PENDING", "Chỉ có thể duyệt đơn nghỉ phép đang chờ xử lý.");
        }

        LeaveBalance balance = leaveBalances.findForUpdate(request.getEmployee().getId(), request.getLeaveType().getId(),
                        (short) request.getStartDate().getYear())
                .orElseThrow(() -> conflict("LEAVE_BALANCE_NOT_FOUND", "Không tìm thấy số dư phép của nhân viên."));
        BigDecimal remainingDays = balance.getAllocatedDays().subtract(balance.getUsedDays());
        if (remainingDays.compareTo(request.getRequestedDays()) < 0) {
            throw conflict("LEAVE_BALANCE_INSUFFICIENT", "Số dư phép hiện tại không đủ để duyệt đơn này.");
        }

        OffsetDateTime reviewedAt = OffsetDateTime.now();
        balance.setUsedDays(balance.getUsedDays().add(request.getRequestedDays()));
        request.setStatus(LeaveRequestStatus.APPROVED);
        request.setReviewedByEmployee(reviewer);
        request.setReviewedAt(reviewedAt);
        request.setRejectionReason(null);
        statusHistories.save(LeaveRequestStatusHistory.builder()
                .leaveRequest(request)
                .oldStatus(LeaveRequestStatus.PENDING)
                .newStatus(LeaveRequestStatus.APPROVED)
                .changedByEmployee(reviewer)
                .changedAt(reviewedAt)
                .build());
        notificationService.notifyApproved(request);

        return LeaveRequestApprovalResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .status(request.getStatus().name())
                .reviewedByEmployeeId(reviewer.getId())
                .reviewedAt(reviewedAt)
                .remainingDays(balance.getAllocatedDays().subtract(balance.getUsedDays()))
                .build();
    }

    private void requireApprovalPermission(Long accountId, Employee reviewer, LeaveRequest request) {
        if (reviewer.getId().equals(request.getEmployee().getId())) {
            throw forbidden("Bạn không thể duyệt đơn nghỉ phép của chính mình.");
        }
        Set<String> roles = accountRoles.findByAccountIdWithRole(accountId).stream()
                .map(AccountRole::getRole)
                .map(role -> role.getCode())
                .collect(Collectors.toSet());
        if (roles.contains("HR")) {
            return;
        }
        if (roles.contains("MANAGER") && assignments.existsByEmployeeIdAndManagerEmployeeIdAndIsCurrentTrue(
                request.getEmployee().getId(), reviewer.getId())) {
            return;
        }
        throw forbidden("Bạn không có quyền duyệt đơn nghỉ phép này.");
    }

    private AuthException forbidden(String message) {
        return new AuthException("AUTH_FORBIDDEN_SCOPE", message, 403);
    }

    private AuthException conflict(String code, String message) {
        return new AuthException(code, message, 409);
    }
}
