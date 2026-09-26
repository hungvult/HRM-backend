package com.hrm.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Username or email is required")
    @Size(min = 3, max = 255, message = "Username or email must be between 3 and 255 characters")
    private String usernameOrEmail;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;

    @Size(max = 500, message = "Device info must not exceed 500 characters")
    private String deviceInfo;
}
