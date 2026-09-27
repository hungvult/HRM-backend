package com.hrm.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String timestamp;
    private int status;
    private String code;
    private String message;
    private String path;
    private String traceId;
    private List<FieldError> errors;

    @Getter
    @Setter
    @Builder
    public static class FieldError {
        private String field;
        private String message;
    }
}
