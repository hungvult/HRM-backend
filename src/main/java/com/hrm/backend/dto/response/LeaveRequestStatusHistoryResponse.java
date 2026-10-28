package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class LeaveRequestStatusHistoryResponse {
    String oldStatus;
    String newStatus;
    Long changedByEmployeeId;
    String changedByEmployeeName;
    OffsetDateTime changedAt;
    String note;
}
