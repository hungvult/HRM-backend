package com.hrm.backend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
public class AttendanceLocationRequest {

    @NotNull(message = "Vĩ độ là bắt buộc.")
    @DecimalMin(value = "-90.0", message = "Vĩ độ phải nằm trong khoảng từ -90 đến 90.")
    @DecimalMax(value = "90.0", message = "Vĩ độ phải nằm trong khoảng từ -90 đến 90.")
    BigDecimal latitude;

    @NotNull(message = "Kinh độ là bắt buộc.")
    @DecimalMin(value = "-180.0", message = "Kinh độ phải nằm trong khoảng từ -180 đến 180.")
    @DecimalMax(value = "180.0", message = "Kinh độ phải nằm trong khoảng từ -180 đến 180.")
    BigDecimal longitude;

    @NotNull(message = "Độ chính xác GPS là bắt buộc.")
    @DecimalMin(value = "0.0", inclusive = false, message = "Độ chính xác GPS phải lớn hơn 0 mét.")
    BigDecimal accuracyMeters;

    @NotNull(message = "Thời điểm lấy vị trí GPS là bắt buộc.")
    Instant locationCapturedAt;
}
