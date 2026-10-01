package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Data;

/** Response tối giản cho API cập nhật thông tin tài khoản. */
@Data
@Builder
public class UpdateAccountResponse {
    private Long id;
    private String username;
    private String email;
    private String status;
}
