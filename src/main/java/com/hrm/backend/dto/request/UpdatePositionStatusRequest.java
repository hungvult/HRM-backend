package com.hrm.backend.dto.request;

import com.hrm.backend.entity.enums.PositionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePositionStatusRequest {
    @NotNull(message = "Trạng thái chức vụ là bắt buộc.")
    private PositionStatus status;

    @Size(max = 1_000, message = "Lý do không được vượt quá 1000 ký tự.")
    private String reason;
}
