package com.hrm.backend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
public class AttendanceLocationRequest {

    @DecimalMin(value = "-90.0", message = "Vĩ độ phải nằm trong khoảng từ -90 đến 90.")
    @DecimalMax(value = "90.0", message = "Vĩ độ phải nằm trong khoảng từ -90 đến 90.")
    BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "Kinh độ phải nằm trong khoảng từ -180 đến 180.")
    @DecimalMax(value = "180.0", message = "Kinh độ phải nằm trong khoảng từ -180 đến 180.")
    BigDecimal longitude;

    @DecimalMin(value = "0.0", inclusive = false, message = "Độ chính xác GPS phải lớn hơn 0 mét.")
    BigDecimal accuracyMeters;

    Instant locationCapturedAt;

    // App chỉ dùng Wi-Fi có thể bỏ cả nhóm GPS; gửi một phần thì trả 400 trước khi ghi công.
    @AssertTrue(message = "GPS phải có đủ vĩ độ, kinh độ, độ chính xác và thời điểm hoặc bỏ toàn bộ.")
    public boolean isGpsGroupComplete() {
        return (latitude == null && longitude == null && accuracyMeters == null && locationCapturedAt == null)
                || (latitude != null && longitude != null && accuracyMeters != null && locationCapturedAt != null);
    }
}
