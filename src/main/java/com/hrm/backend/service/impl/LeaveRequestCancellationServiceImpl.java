package com.hrm.backend.service.impl;

import com.hrm.backend.dto.response.LeaveRequestDetailResponse;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.LeaveRequest;
import com.hrm.backend.entity.LeaveRequestStatusHistory;
import com.hrm.backend.entity.enums.LeaveRequestStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.LeaveRequestRepository;
import com.hrm.backend.repository.LeaveRequestStatusHistoryRepository;
import com.hrm.backend.service.LeaveQueryService;
import com.hrm.backend.service.LeaveRequestCancellationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class LeaveRequestCancellationServiceImpl implements LeaveRequestCancellationService {
    private final EmployeeRepository employees;
    private final LeaveRequestRepository leaveRequests;
    private final LeaveRequestStatusHistoryRepository statusHistories;
    private final LeaveQueryService leaveQueryService;

    @Override
    @Transactional
    public LeaveRequestDetailResponse cancel(Long accountId, Long leaveRequestId) {
        Employee actor = employees.findByAccountId(accountId)
                .orElseThrow(() -> new AuthException("EMPLOYEE_PROFILE_REQUIRED", "Tài khoản chưa có hồ sơ nhân viên.", 403));
        LeaveRequest request = leaveRequests.findWithEmployeeAndLeaveTypeByIdForUpdate(leaveRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("LEAVE_REQUEST_NOT_FOUND", "Không tìm thấy đơn nghỉ phép."));
        if (!request.getEmployee().getId().equals(actor.getId())) {
            throw new AuthException("LEAVE_REQUEST_NOT_OWNER", "Bạn chỉ được hủy đơn nghỉ phép của chính mình.", 403);
        }
        if (request.getStatus() != LeaveRequestStatus.PENDING) {
            throw new AuthException("LEAVE_REQUEST_NOT_CANCELLABLE", "Chỉ có thể hủy đơn nghỉ phép đang chờ xử lý.", 409);
        }

        request.setStatus(LeaveRequestStatus.CANCELLED);
        request.setCancelledAt(OffsetDateTime.now());
        statusHistories.save(LeaveRequestStatusHistory.builder().leaveRequest(request)
                .oldStatus(LeaveRequestStatus.PENDING).newStatus(LeaveRequestStatus.CANCELLED)
                .changedByEmployee(actor).build());

        // Pending requests reserve balance dynamically, so a CANCELLED request is excluded without altering usedDays.
        return leaveQueryService.getLeaveRequestDetail(accountId, leaveRequestId);
    }
}
