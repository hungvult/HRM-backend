package com.hrm.backend.service.impl;

import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.EmployeeAssignment;
import com.hrm.backend.entity.LeaveRequest;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import com.hrm.backend.repository.NotificationRepository;
import com.hrm.backend.service.LeaveRequestNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveRequestNotificationServiceImpl implements LeaveRequestNotificationService {
    private static final String RELATED_TYPE = "LEAVE_REQUEST";
    private final EmployeeAssignmentRepository assignments;
    private final NotificationRepository notifications;

    @Override
    @Transactional
    public void notifySubmitted(LeaveRequest leaveRequest) {
        assignments.findCurrentAssignmentByEmployeeId(leaveRequest.getEmployee().getId())
                .map(EmployeeAssignment::getManagerEmployee)
                .map(manager -> manager.getAccount())
                .ifPresent(managerAccount -> createOnce(managerAccount, "LEAVE_SUBMITTED", leaveRequest,
                        "Có đơn nghỉ phép mới cần xử lý",
                        leaveRequest.getEmployee().getFullName() + " đã gửi đơn " + leaveRequest.getRequestCode() + "."));
    }

    @Override
    @Transactional
    public void notifyApproved(LeaveRequest leaveRequest) {
        notifyEmployee(leaveRequest, "LEAVE_APPROVED", "Đơn nghỉ phép đã được duyệt",
                "Đơn " + leaveRequest.getRequestCode() + " của bạn đã được duyệt.");
    }

    @Override
    @Transactional
    public void notifyRejected(LeaveRequest leaveRequest) {
        notifyEmployee(leaveRequest, "LEAVE_REJECTED", "Đơn nghỉ phép bị từ chối",
                "Đơn " + leaveRequest.getRequestCode() + " của bạn đã bị từ chối.");
    }

    private void notifyEmployee(LeaveRequest request, String type, String title, String content) {
        Account employeeAccount = request.getEmployee().getAccount();
        if (employeeAccount != null) {
            createOnce(employeeAccount, type, request, title, content);
        }
    }

    private void createOnce(Account recipient, String type, LeaveRequest request, String title, String content) {
        String eventKey = type + ":" + request.getId();
        // PostgreSQL ON CONFLICT makes retries and concurrent calls no-ops.
        notifications.insertIfAbsent(recipient.getId(), type, eventKey, title, content, RELATED_TYPE, request.getId());
    }
}
