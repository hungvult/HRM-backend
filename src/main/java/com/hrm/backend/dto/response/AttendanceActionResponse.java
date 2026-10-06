package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Value
@Builder
public class AttendanceActionResponse {
    Long attendanceRecordId;
    LocalDate workDate;
    OffsetDateTime checkInAt;
    OffsetDateTime checkOutAt;
    Integer workingMinutes;
    String message;
}
