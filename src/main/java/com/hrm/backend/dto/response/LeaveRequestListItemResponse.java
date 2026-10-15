package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Value
@Builder
public class LeaveRequestListItemResponse {
    Long id;
    String requestCode;
    Long employeeId;
    String employeeCode;
    String employeeName;
    Long leaveTypeId;
    String leaveTypeCode;
    String leaveTypeName;
    LocalDate startDate;
    LocalDate endDate;
    BigDecimal requestedDays;
    String status;
    OffsetDateTime createdAt;
}
