package com.hrm.backend.service.impl;

import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.EmployeeAssignment;
import com.hrm.backend.entity.LeaveRequest;
import com.hrm.backend.entity.enums.AccountStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.repository.AccountRoleRepository;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import com.hrm.backend.repository.NotificationRepository;
import com.hrm.backend.service.LeaveRequestNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveRequestNotificationServiceImpl implements LeaveRequestNotificationService {
    private static final String RELATED_TYPE = "LEAVE_REQUEST";
    private final EmployeeAssignmentRepository assignments;
    private final AccountRoleRepository accountRoles;
    private final NotificationRepository notifications;

    @Override
    @Transactional
    public void notifySubmitted(LeaveRequest leaveRequest) {
        List<Account> recipients = currentManagerAccount(leaveRequest.getEmployee());
        if (recipients.isEmpty()) recipients = accountRoles.findAccountsByRoleCodeAndAccountStatus("HR", AccountStatus.ACTIVE);
        if (recipients.isEmpty()) throw new AuthException("LEAVE_REQUEST_REVIEWER_NOT_FOUND",
                "Không tìm thấy quản lý trực tiếp hoặc nhân sự đang hoạt động để xử lý đơn nghỉ phép.", 409);
        String content = "Đơn " + leaveRequest.getRequestCode() + " của " + leaveRequest.getEmployee().getFullName() + " đang chờ xử lý.";
        for (Account recipient : recipients) createOnce(recipient, "LEAVE_SUBMITTED", leaveRequest, "Có đơn nghỉ phép chờ xử lý", content);
    }

    @Override @Transactional
    public void notifyApproved(LeaveRequest request) {
        notifyEmployee(request, "LEAVE_APPROVED", "Đơn nghỉ phép đã được duyệt", "Đơn " + request.getRequestCode() + " của bạn đã được duyệt.");
    }

    @Override @Transactional
    public void notifyRejected(LeaveRequest request) {
        notifyEmployee(request, "LEAVE_REJECTED", "Đơn nghỉ phép bị từ chối", "Đơn " + request.getRequestCode() + " của bạn đã bị từ chối.");
    }

    private List<Account> currentManagerAccount(Employee employee) {
        return assignments.findCurrentAssignmentByEmployeeId(employee.getId()).map(EmployeeAssignment::getManagerEmployee)
                .map(Employee::getAccount).filter(account -> account != null && account.getStatus() == AccountStatus.ACTIVE
                        && accountRoles.hasRole(account.getId(), "MANAGER")).map(List::of).orElseGet(List::of);
    }

    private void notifyEmployee(LeaveRequest request, String type, String title, String content) {
        Account account = request.getEmployee().getAccount();
        if (account != null) createOnce(account, type, request, title, content);
    }

    private void createOnce(Account recipient, String type, LeaveRequest request, String title, String content) {
        notifications.insertIfAbsent(recipient.getId(), type, type + ":" + request.getId(), title, content, RELATED_TYPE, request.getId());
    }
}
