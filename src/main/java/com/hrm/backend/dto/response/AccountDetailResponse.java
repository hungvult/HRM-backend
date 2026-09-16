package com.hrm.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Response chi tiết tài khoản, kèm hồ sơ nhân viên nếu tài khoản đã được liên kết. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountDetailResponse {
    private Long id;
    private String username;
    private String email;
    private String status;
    private List<String> roles;
    private EmployeeDto employee;
}
