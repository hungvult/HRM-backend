package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Value
@Builder
public class LeaveRequestApprovalResponse {
    Long id;
    String requestCode;
    String status;
    Long reviewedByEmployeeId;
    OffsetDateTime reviewedAt;
    BigDecimal remainingDays;
}
