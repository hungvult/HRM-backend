package com.hrm.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class UpdateMyProfileRequest {

    @NotBlank(message = "Số điện thoại không được để trống.")
    @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Số điện thoại phải gồm 9 đến 15 chữ số, có thể bắt đầu bằng dấu +.")
    private String phone;

    @NotBlank(message = "Địa chỉ không được để trống.")
    @Size(max = 1000, message = "Địa chỉ không được vượt quá 1000 ký tự.")
    private String address;
}
