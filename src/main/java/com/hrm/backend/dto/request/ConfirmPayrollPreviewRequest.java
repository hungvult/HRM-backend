package com.hrm.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ConfirmPayrollPreviewRequest(@NotBlank String previewToken) { }
