package com.hrm.backend.dto.response;

import com.hrm.backend.entity.enums.AttendanceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class AttendanceAdjustmentHistoryResponse {
    private Long id;
    private OffsetDateTime previousCheckInAt;
    private OffsetDateTime previousCheckOutAt;
    private Integer previousWorkingMinutes;
    private AttendanceStatus previousAttendanceStatus;
    private OffsetDateTime newCheckInAt;
    private OffsetDateTime newCheckOutAt;
    private Integer newWorkingMinutes;
    private AttendanceStatus newAttendanceStatus;
    private String reason;
    private Long adjustedByAccountId;
    private String adjustedByUsername;
    private OffsetDateTime adjustedAt;
}
