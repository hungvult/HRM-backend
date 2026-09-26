package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

@Data @Builder
public class AdminAccountResponse {
    private Long id;
    private String username;
    private String email;
    private String status;
    private List<String> roles;
    private Long employeeId;
    private String employeeCode;
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
