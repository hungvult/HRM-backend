package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder
public class LeaveRequestDetailResponse {
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
    String reason;
    String status;
    Long reviewedByEmployeeId;
    OffsetDateTime reviewedAt;
    String rejectionReason;
    OffsetDateTime cancelledAt;
    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;
    List<LeaveRequestStatusHistoryResponse> statusHistory;
}
