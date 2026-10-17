package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class LeaveRequestRejectionResponse {
    Long id;
    String requestCode;
    String status;
    Long reviewedByEmployeeId;
    OffsetDateTime reviewedAt;
    String rejectionReason;
}
