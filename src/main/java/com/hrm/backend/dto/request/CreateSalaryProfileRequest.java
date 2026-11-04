package com.hrm.backend.dto.request;

import com.hrm.backend.json.StrictLongDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateSalaryProfileRequest {
    @NotNull(message = "employeeId không được để trống.")
    @Positive(message = "employeeId phải là số nguyên dương.")
    @JsonDeserialize(using = StrictLongDeserializer.class)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    private Long employeeId;

    @NotNull(message = "effectiveFrom không được để trống.")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date", example = "2026-11-01")
    private LocalDate effectiveFrom;

    @NotNull(message = "baseSalary không được để trống.")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "16000000")
    private BigDecimal baseSalary;

    @NotNull(message = "allowance không được để trống.")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2500000")
    private BigDecimal allowance;

    @NotNull(message = "deduction không được để trống.")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private BigDecimal deduction;
}
