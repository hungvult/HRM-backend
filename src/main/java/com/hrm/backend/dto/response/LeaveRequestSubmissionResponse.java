package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Value
@Builder
public class LeaveRequestSubmissionResponse {
    Long id;
    String requestCode;
    Long leaveTypeId;
    LocalDate startDate;
    LocalDate endDate;
    BigDecimal requestedDays;
    String status;
    OffsetDateTime createdAt;
}
