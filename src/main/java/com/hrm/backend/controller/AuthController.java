package com.hrm.backend.controller;

import com.hrm.backend.dto.request.LoginRequest;
import com.hrm.backend.dto.response.LoginResponse;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.security.JwtService;
import com.hrm.backend.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(loginRequest);

        setRefreshTokenCookie(response, loginResponse.getRefreshToken(), 7 * 24 * 60 * 60);

        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = WebUtils.getCookie(request, REFRESH_TOKEN_COOKIE_NAME);
        String refreshToken = cookie != null ? cookie.getValue() : null;

        LoginResponse loginResponse = authService.refreshToken(refreshToken);

        setRefreshTokenCookie(response, loginResponse.getRefreshToken(), 7 * 24 * 60 * 60);

        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal CustomUserDetails userDetails, 
                                       @RequestHeader(value = "Authorization", required = false) String authHeader,
                                       HttpServletResponse response) {
        
        Long sessionId = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                sessionId = jwtService.getSessionIdFromToken(jwtService.validateAndGetClaims(token));
            } catch (Exception ignored) {
                // If token is invalid/expired, we just ignore and clear cookie
            }
        }

        authService.logout(userDetails.getAccount().getId(), sessionId);
        
        // Clear cookie
        setRefreshTokenCookie(response, "", 0);

        return ResponseEntity.noContent().build();
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token, int maxAge) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // Should be true in production (requires HTTPS)
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
