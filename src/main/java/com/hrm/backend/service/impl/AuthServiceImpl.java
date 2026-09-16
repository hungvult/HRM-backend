package com.hrm.backend.service.impl;

import com.hrm.backend.dto.request.LoginRequest;
import com.hrm.backend.dto.response.LoginResponse;
import com.hrm.backend.dto.response.LoginUserResponse;
import com.hrm.backend.entity.*;
import com.hrm.backend.entity.enums.AccountStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.repository.*;
import com.hrm.backend.security.JwtService;
import com.hrm.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.OffsetDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AccountRepository accountRepository;
    private final AuthSessionRepository authSessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final HttpServletRequest request;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest dto) {
        String identity = dto.getUsernameOrEmail().trim();
        Account account = accountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(identity).orElseThrow(this::invalidCredentials);
        assertActive(account);
        if (!passwordEncoder.matches(dto.getPassword(), account.getPasswordHash())) throw invalidCredentials();
        account.setLastLoginAt(OffsetDateTime.now());
        LoginResponse result = issueTokens(account, dto.getDeviceInfo(), request.getHeader("User-Agent"));
        audit(account, "AUTH_LOGIN", "accounts", account.getId()); return result;
    }
    @Override
    @Transactional
    public LoginResponse refreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw invalidRefreshToken();
        AuthSession old = authSessionRepository.findByRefreshTokenHash(hash(rawToken)).orElseThrow(this::invalidRefreshToken);
        OffsetDateTime now = OffsetDateTime.now();
        if (old.getRevokedAt() != null || !old.getExpiresAt().isAfter(now)) throw invalidRefreshToken();
        assertActive(old.getAccount()); old.setLastUsedAt(now); old.setRevokedAt(now); old.setRevokeReason("TOKEN_ROTATED");
        return issueTokens(old.getAccount(), old.getDeviceInfo(), request.getHeader("User-Agent"));
    }
    @Override
    @Transactional
    public void logout(Long accountId, Long sessionId) {
        if (sessionId == null) return;
        authSessionRepository.findById(sessionId).ifPresent(s -> { if (s.getAccount().getId().equals(accountId) && s.getRevokedAt() == null) { s.setRevokedAt(OffsetDateTime.now()); s.setRevokeReason("LOGOUT"); audit(s.getAccount(), "AUTH_LOGOUT", "auth_sessions", s.getId()); } });
    }
    private LoginResponse issueTokens(Account account, String device, String agent) {
        OffsetDateTime now = OffsetDateTime.now(); String raw = jwtService.generateRefreshToken();
        AuthSession session = authSessionRepository.save(AuthSession.builder().account(account).refreshTokenHash(hash(raw)).issuedAt(now).expiresAt(now.plusDays(7)).deviceInfo(device).userAgent(agent).ipAddress(ip()).build());
        var roles = accountRoleRepository.findByAccountIdWithRole(account.getId()).stream()
                .map(AccountRole::getRole)
                .map(Role::getCode)
                .sorted()
                .toList();
        LoginUserResponse user = LoginUserResponse.builder()
                .id(account.getId())
                .username(account.getUsername())
                .roles(roles)
                .build();
        return LoginResponse.builder()
                .accessToken(jwtService.generateAccessToken(account.getId(), account.getUsername(), roles, session.getId()))
                .expiresIn(jwtService.getAccessTokenExpirationSeconds())
                .user(user)
                .refreshToken(raw)
                .build();
    }
    private void assertActive(Account a) {
        if (a.getStatus() == AccountStatus.LOCKED) throw new AuthException("AUTH_ACCOUNT_LOCKED", "Tài khoản đã bị khóa.", 403);
        if (a.getStatus() == AccountStatus.DISABLED) throw new AuthException("AUTH_ACCOUNT_DISABLED", "Tài khoản đã bị vô hiệu hóa.", 403);
    }
    private AuthException invalidCredentials() { return new AuthException("AUTH_INVALID_CREDENTIALS", "Tên đăng nhập/email hoặc mật khẩu không đúng.", 401); }
    private AuthException invalidRefreshToken() { return new AuthException("AUTH_REFRESH_TOKEN_INVALID", "Refresh token không hợp lệ hoặc đã hết hạn.", 401); }
    private String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String ip() {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim(); }
    private void audit(Account a, String action, String type, Long id) {
        auditLogRepository.save(AuditLog.builder()
                .actorAccount(a)
                .action(action)
                .entityType(type)
                .entityId(id)
                .occurredAt(OffsetDateTime.now())
                .ipAddress(ip())
                .build());
    }
}
