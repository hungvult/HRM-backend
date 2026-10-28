package com.hrm.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateLeaveRequest {
    @NotNull(message = "Loại phép là bắt buộc.")
    private Long leaveTypeId;

    @NotNull(message = "Ngày bắt đầu nghỉ là bắt buộc.")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc nghỉ là bắt buộc.")
    private LocalDate endDate;

    @NotBlank(message = "Lý do nghỉ là bắt buộc.")
    @Size(max = 2000, message = "Lý do nghỉ không được vượt quá 2000 ký tự.")
    private String reason;
}
