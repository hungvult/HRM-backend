package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class LeaveBalanceResponse {
    Long leaveTypeId;
    String leaveTypeCode;
    String leaveTypeName;
    BigDecimal allocatedDays;
    BigDecimal usedDays;
    BigDecimal remainingDays;
}
