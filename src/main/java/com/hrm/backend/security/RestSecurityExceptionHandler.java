package com.hrm.backend.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Chuẩn hóa JSON error cho cả lỗi chưa xác thực (401) và thiếu quyền (403). */
@Component
@RequiredArgsConstructor
public class RestSecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final SecurityErrorResponseWriter errorWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        errorWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                "AUTH_UNAUTHORIZED", "Bạn cần đăng nhập để truy cập tài nguyên này.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException, ServletException {
        errorWriter.write(request, response, HttpServletResponse.SC_FORBIDDEN,
                "AUTH_FORBIDDEN", "Bạn không có quyền truy cập tài nguyên này.");
    }
}
