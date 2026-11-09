package com.hrm.backend.exception;

import com.hrm.backend.dto.response.ErrorResponse;

import java.util.List;

public class AuthException extends RuntimeException {
    private final String code;
    private final int status;
    private final List<ErrorResponse.FieldError> errors;

    public AuthException(String code, String message, int status) {
        this(code, message, status, null);
    }

    public AuthException(String code, String message, int status, List<ErrorResponse.FieldError> errors) {
        super(message);
        this.code = code;
        this.status = status;
        this.errors = errors;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }

    public List<ErrorResponse.FieldError> getErrors() {
        return errors;
    }
}
