package com.hrm.backend.filter;

import com.hrm.backend.entity.ApiLog;
import com.hrm.backend.repository.ApiLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Locale;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class ApiLoggingFilter extends OncePerRequestFilter {

    private final ApiLogRepository apiLogRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        long startedAt = System.currentTimeMillis();

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            try {
                saveApiLog(wrappedRequest, wrappedResponse, System.currentTimeMillis() - startedAt);
            } catch (Exception ex) {
                log.warn("Could not persist API log", ex);
            } finally {
                wrappedResponse.copyBodyToResponse();
            }
        }
    }

    private void saveApiLog(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, long elapsedMs) {
        String requestBody = readBody(request.getContentAsByteArray(), request.getCharacterEncoding());
        String responseBody = readBody(response.getContentAsByteArray(), response.getCharacterEncoding());
        String requestUrl = buildRequestUrl(request);
        String tokenCode = request.getHeader("Authorization");
        String method = request.getMethod();
        String url = request.getRequestURI();
        int status = response.getStatus();

        ApiLog apiLog = ApiLog.builder()
                .content(buildContent(method, requestUrl, tokenCode, requestBody, responseBody, status))
                .command(buildCommand(method, url))
                .url(url)
                .ip(getClientIp(request))
                .occurredAt(OffsetDateTime.now())
                .level("Information")
                .httpMethod(method)
                .requestUrl(requestUrl)
                .tokenCode(tokenCode)
                .requestBody(requestBody)
                .responseBody(responseBody)
                .responseStatus(status)
                .executionTimeMs(elapsedMs)
                .build();

        apiLogRepository.save(apiLog);
    }

    private String buildContent(String method, String requestUrl, String tokenCode, String requestBody, String responseBody, int status) {
        return "ApiLoggingFilter: Received Response \r\n"
                + "Argument raw data:  \r\n"
                + "Method: " + nullToBlank(method) + " \r\n"
                + "Url: " + nullToBlank(requestUrl) + " \r\n"
                + "TokenCode: " + nullToBlank(tokenCode) + " \r\n"
                + "Request body: " + nullToBlank(requestBody) + " \r\n"
                + "Response body: " + nullToBlank(responseBody) + "\r\n"
                + "Response status: " + status;
    }

    private String buildRequestUrl(HttpServletRequest request) {
        String queryString = request.getQueryString();
        StringBuffer url = request.getRequestURL();
        return queryString == null || queryString.isBlank() ? url.toString() : url.append('?').append(queryString).toString();
    }

    private String buildCommand(String method, String url) {
        String normalizedMethod = method == null || method.isBlank()
                ? "unknown"
                : method.toLowerCase(Locale.ROOT);
        String normalizedPath = url == null ? "unknown" : url
                .replaceAll("^/+", "")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toLowerCase(Locale.ROOT);
        return "_raw_http_method_" + normalizedMethod + "_" + normalizedPath;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    private String readBody(byte[] body, String encoding) {
        if (body == null || body.length == 0) {
            return "";
        }

        Charset charset;
        try {
            charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        } catch (Exception ignored) {
            charset = StandardCharsets.UTF_8;
        }

        return new String(body, charset);
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
