package com.hrm.backend.service.impl;

import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.LeaveRequest;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import com.hrm.backend.repository.NotificationRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LeaveRequestNotificationServiceImplTest {

    private final EmployeeAssignmentRepository assignments = mock(EmployeeAssignmentRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final LeaveRequestNotificationServiceImpl service =
            new LeaveRequestNotificationServiceImpl(assignments, notifications);

    @Test
    void retryingApprovalUsesTheSameEventKey() {
        Account account = Account.builder().id(41L).build();
        Employee employee = Employee.builder().account(account).build();
        LeaveRequest request = LeaveRequest.builder().id(72L).requestCode("NP000072").employee(employee).build();

        service.notifyApproved(request);
        service.notifyApproved(request);

        verify(notifications, times(2)).insertIfAbsent(
                eq(41L), eq("LEAVE_APPROVED"), eq("LEAVE_APPROVED:72"),
                eq("Đơn nghỉ phép đã được duyệt"),
                eq("Đơn NP000072 của bạn đã được duyệt."),
                eq("LEAVE_REQUEST"), eq(72L));
    }
}
