package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Response tối giản cho API thay thế vai trò tài khoản. */
@Data
@Builder
public class ReplaceAccountRolesResponse {
    private Long id;
    private List<String> roles;
}
