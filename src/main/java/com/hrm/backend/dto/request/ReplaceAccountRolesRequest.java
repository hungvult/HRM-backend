package com.hrm.backend.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.util.Set;

@Data
public class ReplaceAccountRolesRequest {
    @NotEmpty
    private Set<@Pattern(regexp = "ADMIN|HR|MANAGER|EMPLOYEE") String> roles;
}
