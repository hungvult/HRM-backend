package com.hrm.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.attendance")
public class AttendanceProperties {

    @NotBlank
    private String companyTimezone = "Asia/Ho_Chi_Minh";

    @Valid
    @NotEmpty(message = "Phải cấu hình ít nhất một nguồn Wi-Fi công ty tin cậy.")
    private List<TrustedWifiSource> trustedSources = new ArrayList<>();

    /**
     * Proxy được phép chuyển tiếp địa chỉ IP của client. Nếu danh sách này rỗng,
     * X-Forwarded-For luôn bị bỏ qua và hệ thống dùng địa chỉ TCP trực tiếp.
     */
    @Valid
    private List<TrustedProxy> trustedProxies = new ArrayList<>();

    public ZoneId companyZoneId() {
        return ZoneId.of(companyTimezone);
    }

    @Getter
    @Setter
    public static class TrustedWifiSource {
        @NotBlank
        private String cidr;

        @NotNull
        private Long wifiNetworkId;
    }

    @Getter
    @Setter
    public static class TrustedProxy {
        @NotBlank
        private String cidr;
    }
}
