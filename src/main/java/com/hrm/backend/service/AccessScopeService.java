package com.hrm.backend.service;

import java.time.LocalDate;

public interface AccessScopeService {
    void requireCanReadEmployee(Long actorAccountId, Long targetEmployeeId);

    void requireCanReadEmployeeAtDate(Long actorAccountId, Long targetEmployeeId, LocalDate workDate);

    Long managerEmployeeIdForAttendanceScope(Long actorAccountId);

    void requireCanReadLeaveRequest(Long actorAccountId, Long requestEmployeeId);

    /**
     * Resolves the employee visibility policy shared by leave-request list and detail APIs.
     * HR/ADMIN have company-wide scope; other callers can see only themselves and their current reports.
     */
    LeaveRequestReadScope resolveLeaveRequestReadScope(Long actorAccountId);
}
