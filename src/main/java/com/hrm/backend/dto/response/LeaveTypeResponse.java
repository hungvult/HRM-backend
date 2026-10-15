package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class LeaveTypeResponse {
    Long id;
    String code;
    String name;
    BigDecimal annualQuotaDays;
    Boolean paid;
}
