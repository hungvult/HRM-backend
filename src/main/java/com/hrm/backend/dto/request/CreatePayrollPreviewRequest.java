package com.hrm.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreatePayrollPreviewRequest(
        @NotNull @Min(2000) @Max(2100) Short year,
        @NotNull @Min(1) @Max(12) Short month,
        @Min(1) Long regenerateRunId) {
    // Client cũ chỉ gửi kỳ vẫn tạo preview thông thường.
    public CreatePayrollPreviewRequest(Short year, Short month) { this(year, month, null); }
}
