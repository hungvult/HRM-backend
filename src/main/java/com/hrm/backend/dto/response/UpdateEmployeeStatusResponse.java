package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class UpdateEmployeeStatusResponse {
    private Long id;
    private String employeeCode;
    private String fullName;
    private String employmentStatus;
    private OffsetDateTime statusChangedAt;
    private Long statusChangedByAccountId;
    private String reason;
}
