package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class DepartmentResponse {
    private final Long id;
    private final String code;
    private final String name;
    private final String description;
    private final String status;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
}
