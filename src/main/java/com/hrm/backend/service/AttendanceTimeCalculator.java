package com.hrm.backend.service;

import com.hrm.backend.entity.enums.AttendanceStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public interface AttendanceTimeCalculator {
    Integer calculateWorkingMinutes(LocalDate workDate, OffsetDateTime checkInAt, OffsetDateTime checkOutAt,
                                    AttendanceStatus status);
}
