package com.hrm.backend.service;

public interface AccessScopeService {
    void requireCanReadEmployee(Long actorAccountId, Long targetEmployeeId);
}
