package com.hrm.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CurrentUserResponse {
    private Long id;
    private String username;
    private String email;
    private List<String> roles;
    private EmployeeDto employee;
}
