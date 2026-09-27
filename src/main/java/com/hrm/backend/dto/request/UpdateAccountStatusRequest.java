package com.hrm.backend.dto.request;

import com.hrm.backend.entity.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAccountStatusRequest {
    @NotNull
    private AccountStatus status;
    @Size(max = 100)
    private String reason;
}
