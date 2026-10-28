package com.hrm.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectLeaveRequest {
    @NotBlank(message = "Lý do từ chối là bắt buộc.")
    @Size(max = 2000, message = "Lý do từ chối không được vượt quá 2000 ký tự.")
    private String reason;
}
