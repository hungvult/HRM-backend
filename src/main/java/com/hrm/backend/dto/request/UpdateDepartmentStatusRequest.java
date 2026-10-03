package com.hrm.backend.dto.request;

import com.hrm.backend.entity.enums.DepartmentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateDepartmentStatusRequest {

    @NotNull(message = "Trạng thái phòng ban là bắt buộc.")
    private DepartmentStatus status;

    @Size(max = 1_000, message = "Lý do không được vượt quá 1000 ký tự.")
    private String reason;
}
