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
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
    private final EntityManager entityManager;

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
    // Cookie refresh trỏ tới một auth_sessions. Khóa và đọc lại phiên trước khi xoay token để logout không bị vượt qua.
    public LoginResponse refreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw invalidRefreshToken();
        AuthSession old = authSessionRepository.findByRefreshTokenHash(hash(rawToken)).orElseThrow(this::invalidRefreshToken);
        // Lượt khác có thể đã thu hồi phiên sau truy vấn trên. Refresh dưới khóa loại bỏ cả dữ liệu cũ trong persistence context.
        entityManager.refresh(old, LockModeType.PESSIMISTIC_WRITE);
        OffsetDateTime now = OffsetDateTime.now();
        // Ưu tiên trả lỗi trạng thái account thay vì che thành refresh token invalid
        // khi session đã bị thu hồi do account bị khóa/vô hiệu hóa.
        assertActive(old.getAccount());
        if (old.getRevokedAt() != null || !old.getExpiresAt().isAfter(now)) throw invalidRefreshToken();
        old.setLastUsedAt(now); old.setRevokedAt(now); old.setRevokeReason("TOKEN_ROTATED");
        return issueTokens(old.getAccount(), old.getDeviceInfo(), request.getHeader("User-Agent"));
    }
    @Override
    @Transactional
    // accountId lấy từ người dùng xác thực; sessionId lấy từ JWT. Chỉ thu hồi đúng phiên của tài khoản này.
    public void logout(Long accountId, Long sessionId) {
        if (sessionId == null) throw invalidSession();
        AuthSession session = authSessionRepository.findById(sessionId).orElseThrow(this::invalidSession);
        if (!session.getAccount().getId().equals(accountId)) throw invalidSession();

        // Dùng cùng khóa với refresh và đọc lại trạng thái dù bộ lọc JWT đã kiểm tra phiên trước đó.
        entityManager.refresh(session, LockModeType.PESSIMISTIC_WRITE);
        if (!session.getAccount().getId().equals(accountId)) throw invalidSession();
        if (session.getRevokedAt() != null) {
            // Hai lượt logout cùng phiên được phép hoàn tất; phiên đã xoay phải trả 401 để client hủy phiên mới.
            if ("LOGOUT".equals(session.getRevokeReason())) return;
            throw invalidSession();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (!session.getExpiresAt().isAfter(now)) throw invalidSession();

        // Giữ khóa tới khi transaction commit, ghi thu hồi và audit cùng transaction trước khi controller xóa cookie.
        session.setRevokedAt(now);
        session.setRevokeReason("LOGOUT");
        audit(session.getAccount(), "AUTH_LOGOUT", "auth_sessions", session.getId());
    }

    // Logout gặp phiên đã xoay/không thuộc người dùng phải yêu cầu xác thực lại, tránh báo hủy thành công sai phiên.
    private AuthException invalidSession() {
        return new AuthException("AUTH_UNAUTHORIZED", "Phiên đăng nhập đã bị thu hồi hoặc không còn hợp lệ.", 401);
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
