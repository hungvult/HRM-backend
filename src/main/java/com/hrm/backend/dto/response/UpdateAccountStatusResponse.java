package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Data;

/** Response tối giản cho API cập nhật trạng thái tài khoản. */
@Data
@Builder
public class UpdateAccountStatusResponse {
    private Long id;
    private String status;
}
