package com.hrm.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

/** Response tối giản chỉ dùng cho endpoint làm mới access token. */
@Data
@Builder
public class RefreshTokenResponse {
    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;
    private User user;

    /** Chỉ để controller thiết lập HttpOnly cookie, không được serialize ra JSON. */
    @JsonIgnore
    private String refreshToken;

    @Data
    @Builder
    public static class User {
        private Long id;
    }
}
