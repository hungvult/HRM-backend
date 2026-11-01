package com.hrm.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAccountRequest {
    @Size(min = 3, max = 100)
    private String username;
    @Email @Size(max = 255)
    private String email;
    // Web gửi cùng thông tin tài khoản; client cũ bỏ trường này thì giữ role hiện tại.
    @jakarta.validation.constraints.Size(min = 1, max = 4, message = "Vui lòng chọn ít nhất một vai trò hợp lệ.")
    private java.util.Set<@jakarta.validation.constraints.NotBlank String> roles;

}
