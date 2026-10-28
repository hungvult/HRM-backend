package com.hrm.backend.dto.response;

import com.hrm.backend.entity.enums.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Builder
@AllArgsConstructor
public class TodayAttendanceResponse {
    private final LocalDate workDate;
    private final OffsetDateTime checkInAt;
    private final OffsetDateTime checkOutAt;
    private final Integer workingMinutes;
    private final AttendanceStatus attendanceStatus;
    private final boolean checkedIn;
    private final boolean checkedOut;
}
