package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class CurrentSalaryProfileResponse {
    Long id;
    LocalDate effectiveFrom;
    LocalDate effectiveTo;
    BigDecimal baseSalary;
    BigDecimal allowance;
    BigDecimal deduction;
}
