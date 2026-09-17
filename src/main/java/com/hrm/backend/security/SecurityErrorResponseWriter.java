package com.hrm.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrm.backend.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {
    private final ObjectMapper objectMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String code, String message)
            throws IOException {
        String traceId = (String) request.getAttribute("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            request.setAttribute("traceId", traceId);
        }
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .status(status)
                .code(code)
                .message(message)
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
