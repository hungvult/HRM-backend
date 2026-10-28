package com.hrm.backend.service;

import com.hrm.backend.entity.LeaveRequest;

public interface LeaveRequestNotificationService {
    void notifySubmitted(LeaveRequest leaveRequest);
    void notifyApproved(LeaveRequest leaveRequest);
    void notifyRejected(LeaveRequest leaveRequest);
}
