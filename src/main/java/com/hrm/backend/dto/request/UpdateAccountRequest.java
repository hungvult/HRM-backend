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
}
