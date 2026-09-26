package com.hrm.backend.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.Set;

@Data
public class CreateAccountRequest {
    @NotBlank
    @Size(min = 3, max = 100)
    private String username;
    @NotBlank
    @Email
    @Size(max = 255)
    private String email;
    @NotBlank
    @Size(min = 8, max = 128)
    private String password;
    @NotEmpty
    private Set<@Pattern(regexp = "ADMIN|HR|MANAGER|EMPLOYEE") String> roles;

    /** Hồ sơ nhân viên đã được tạo trước đó; mỗi nhân viên chỉ có một tài khoản. */
    @NotNull
    @Positive
    private Long employeeId;
}
