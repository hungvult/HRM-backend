package com.hrm.backend.service;

import java.time.LocalDate;

public interface AccessScopeService {
    void requireCanReadEmployee(Long actorAccountId, Long targetEmployeeId);

    void requireCanReadEmployeeAtDate(Long actorAccountId, Long targetEmployeeId, LocalDate workDate);

    Long managerEmployeeIdForAttendanceScope(Long actorAccountId);
}
