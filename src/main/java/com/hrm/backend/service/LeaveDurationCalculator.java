package com.hrm.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Calculates leave duration server-side; holiday/shift rules can replace this implementation later. */
public interface LeaveDurationCalculator {
    BigDecimal calculate(LocalDate startDate, LocalDate endDate);
}
