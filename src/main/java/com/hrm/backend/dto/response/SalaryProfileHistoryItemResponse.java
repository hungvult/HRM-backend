package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Value
@Builder
public class SalaryProfileHistoryItemResponse {
    Long id;
    LocalDate effectiveFrom;
    LocalDate effectiveTo;
    BigDecimal baseSalary;
    BigDecimal allowance;
    BigDecimal deduction;
    boolean editable;
    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;
}
