package com.hrm.backend.service.impl;

import com.hrm.backend.dto.request.RejectLeaveRequest;
import com.hrm.backend.dto.response.LeaveRequestRejectionResponse;
import com.hrm.backend.entity.AccountRole;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.LeaveRequest;
import com.hrm.backend.entity.LeaveRequestStatusHistory;
import com.hrm.backend.entity.enums.LeaveRequestStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.AccountRoleRepository;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.LeaveRequestRepository;
import com.hrm.backend.repository.LeaveRequestStatusHistoryRepository;
import com.hrm.backend.service.LeaveRequestNotificationService;
import com.hrm.backend.service.LeaveRequestRejectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveRequestRejectionServiceImpl implements LeaveRequestRejectionService {
    private final LeaveRequestRepository leaveRequests;
    private final LeaveRequestStatusHistoryRepository statusHistories;
    private final EmployeeRepository employees;
    private final EmployeeAssignmentRepository assignments;
    private final AccountRoleRepository accountRoles;
    private final LeaveRequestNotificationService notificationService;

    @Override
    @Transactional
    public LeaveRequestRejectionResponse reject(Long accountId, Long leaveRequestId, RejectLeaveRequest rejection) {
        LeaveRequest request = leaveRequests.findWithEmployeeAndLeaveTypeByIdForUpdate(leaveRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("LEAVE_REQUEST_NOT_FOUND", "Không tìm thấy đơn nghỉ phép."));
        Employee reviewer = employees.findByAccountId(accountId)
                .orElseThrow(() -> forbidden("Tài khoản chưa có hồ sơ nhân viên."));

        requireRejectionPermission(accountId, reviewer, request);
        if (request.getStatus() != LeaveRequestStatus.PENDING) {
            throw conflict("LEAVE_REQUEST_NOT_PENDING", "Chỉ có thể từ chối đơn nghỉ phép đang chờ xử lý.");
        }

        String reason = rejection.getReason().trim();
        OffsetDateTime reviewedAt = OffsetDateTime.now();
        request.setStatus(LeaveRequestStatus.REJECTED);
        request.setReviewedByEmployee(reviewer);
        request.setReviewedAt(reviewedAt);
        request.setRejectionReason(reason);
        statusHistories.save(LeaveRequestStatusHistory.builder()
                .leaveRequest(request)
                .oldStatus(LeaveRequestStatus.PENDING)
                .newStatus(LeaveRequestStatus.REJECTED)
                .changedByEmployee(reviewer)
                .changedAt(reviewedAt)
                .note(reason)
                .build());
        notificationService.notifyRejected(request);

        return LeaveRequestRejectionResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .status(request.getStatus().name())
                .reviewedByEmployeeId(reviewer.getId())
                .reviewedAt(reviewedAt)
                .rejectionReason(reason)
                .build();
    }

    private void requireRejectionPermission(Long accountId, Employee reviewer, LeaveRequest request) {
        if (reviewer.getId().equals(request.getEmployee().getId())) {
            throw forbidden("Bạn không thể từ chối đơn nghỉ phép của chính mình.");
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
        throw forbidden("Bạn không có quyền từ chối đơn nghỉ phép này.");
    }

    private AuthException forbidden(String message) {
        return new AuthException("AUTH_FORBIDDEN_SCOPE", message, 403);
    }

    private AuthException conflict(String code, String message) {
        return new AuthException(code, message, 409);
    }
}
