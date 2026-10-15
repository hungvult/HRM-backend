package com.hrm.backend.service;

import com.hrm.backend.entity.LeaveRequest;

/**
 * Boundary used by the leave-request command service after a successful
 * submission or status transition. Implementations are idempotent, so callers
 * may safely invoke these methods when retrying an HTTP request.
 */
public interface LeaveRequestNotificationService {
    void notifySubmitted(LeaveRequest leaveRequest);

    void notifyApproved(LeaveRequest leaveRequest);

    void notifyRejected(LeaveRequest leaveRequest);
}
