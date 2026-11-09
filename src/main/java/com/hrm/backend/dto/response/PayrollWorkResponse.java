package com.hrm.backend.dto.response;

import java.math.BigDecimal;

public record PayrollWorkResponse(Integer standardWorkingDays, Integer standardMinutesPerDay,
                                  Integer paidMinutes, BigDecimal paidWorkingDays) { }
