package com.hrm.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdateSalaryProfileRequest {
    @NotNull(message = "effectiveFrom không được để trống.")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date", example = "2026-11-01")
    private LocalDate effectiveFrom;

    @NotNull(message = "baseSalary không được để trống.")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "16500000")
    private BigDecimal baseSalary;

    @NotNull(message = "allowance không được để trống.")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2500000")
    private BigDecimal allowance;

    @NotNull(message = "deduction không được để trống.")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private BigDecimal deduction;
}
