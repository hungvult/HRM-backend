package com.hrm.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePositionRequest {
    @NotBlank(message = "Tên chức vụ là bắt buộc.")
    @Size(max = 150, message = "Tên chức vụ không được vượt quá 150 ký tự.")
    private String name;

    @Size(max = 10_000, message = "Mô tả không được vượt quá 10000 ký tự.")
    private String description;

    @NotNull(message = "Cấp bậc chức vụ là bắt buộc.")
    @Min(value = 1, message = "Cấp bậc chức vụ phải từ 1 đến 10.")
    @Max(value = 10, message = "Cấp bậc chức vụ phải từ 1 đến 10.")
    private Integer rankLevel;
}
