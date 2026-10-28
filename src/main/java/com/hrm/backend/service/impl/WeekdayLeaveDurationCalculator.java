package com.hrm.backend.service.impl;

import com.hrm.backend.service.LeaveDurationCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

/** Default PM-neutral rule: each weekday counts as one leave day; Saturdays and Sundays do not. */
@Component
public class WeekdayLeaveDurationCalculator implements LeaveDurationCalculator {
    @Override
    public BigDecimal calculate(LocalDate startDate, LocalDate endDate) {
        long workingDays = startDate.datesUntil(endDate.plusDays(1))
                .filter(day -> day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY)
                .count();
        return BigDecimal.valueOf(workingDays);
    }
}
