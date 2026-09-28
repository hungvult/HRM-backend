package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Thông tin người dùng tối giản chỉ dùng trong response đăng nhập/làm mới token. */
@Data
@Builder
public class LoginUserResponse {
    private Long id;
    private String username;
    private List<String> roles;
}
