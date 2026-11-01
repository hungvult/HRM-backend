package com.hrm.backend.service.impl;

import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.AuthSession;
import com.hrm.backend.entity.enums.AccountStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.repository.*;
import com.hrm.backend.security.JwtService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final AuditLogRepository audits = mock(AuditLogRepository.class);
    private final AccountRoleRepository roles = mock(AccountRoleRepository.class);
    private final JwtService jwt = mock(JwtService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final AuthServiceImpl service = new AuthServiceImpl(accounts, sessions, audits, roles,
            mock(PasswordEncoder.class), jwt, request, entityManager);

    private AuthSession session() {
        Account account = new Account();
        account.setId(7L);
        account.setUsername("qa_auth_fixture");
        account.setStatus(AccountStatus.ACTIVE);
        return AuthSession.builder().id(9L).account(account).expiresAt(OffsetDateTime.now().plusDays(1)).build();
    }

    @Test
    void refreshRechecksRevocationAfterAcquiringTheLock() {
        AuthSession session = session();
        when(sessions.findByRefreshTokenHash(anyString())).thenReturn(Optional.of(session));
        doAnswer(invocation -> {
            session.setRevokedAt(OffsetDateTime.now());
            session.setRevokeReason("LOGOUT");
            return null;
        }).when(entityManager).refresh(session, LockModeType.PESSIMISTIC_WRITE);

        AuthException error = assertThrows(AuthException.class, () -> service.refreshToken("fixture-refresh"));

        assertEquals(401, error.getStatus());
        verify(sessions, never()).save(any());
    }

    @Test
    void logoutRejectsSessionRotatedWhileWaitingForTheLock() {
        AuthSession session = session();
        when(sessions.findById(9L)).thenReturn(Optional.of(session));
        doAnswer(invocation -> {
            session.setRevokedAt(OffsetDateTime.now());
            session.setRevokeReason("TOKEN_ROTATED");
            return null;
        }).when(entityManager).refresh(session, LockModeType.PESSIMISTIC_WRITE);

        AuthException error = assertThrows(AuthException.class, () -> service.logout(7L, 9L));

        assertEquals(401, error.getStatus());
        assertEquals("TOKEN_ROTATED", session.getRevokeReason());
        verifyNoInteractions(audits);
    }

    @Test
    void logoutRevokesAndAuditsOnlyItsOwnSessionUnderTheLock() {
        AuthSession session = session();
        when(sessions.findById(9L)).thenReturn(Optional.of(session));

        service.logout(7L, 9L);

        verify(entityManager).refresh(session, LockModeType.PESSIMISTIC_WRITE);
        assertNotNull(session.getRevokedAt());
        assertEquals("LOGOUT", session.getRevokeReason());
        verify(audits).save(argThat(audit -> audit.getEntityId().equals(9L)
                && audit.getActorAccount().getId().equals(7L) && audit.getAction().equals("AUTH_LOGOUT")));
        verify(sessions, never()).revokeActiveByAccountId(anyLong(), any(), anyString());
    }

    @Test
    void anotherAccountsSessionCannotBeLockedOrRevoked() {
        AuthSession session = session();
        when(sessions.findById(9L)).thenReturn(Optional.of(session));

        assertThrows(AuthException.class, () -> service.logout(8L, 9L));

        assertNull(session.getRevokedAt());
        verifyNoInteractions(entityManager, audits);
    }

    @Test
    void concurrentDuplicateLogoutDoesNotDuplicateAudit() {
        AuthSession session = session();
        session.setRevokedAt(OffsetDateTime.now());
        session.setRevokeReason("LOGOUT");
        when(sessions.findById(9L)).thenReturn(Optional.of(session));

        service.logout(7L, 9L);

        verify(entityManager).refresh(session, LockModeType.PESSIMISTIC_WRITE);
        verifyNoInteractions(audits);
    }

    @Test
    void refreshRotatesAnActiveSessionAfterLockingAndKeepsTheAccountAndDevice() {
        AuthSession session = session();
        session.setDeviceInfo("fixture-device");
        when(sessions.findByRefreshTokenHash(anyString())).thenReturn(Optional.of(session));
        when(jwt.generateRefreshToken()).thenReturn("new-refresh-fixture");
        when(sessions.save(any())).thenAnswer(invocation -> {
            AuthSession created = invocation.getArgument(0);
            created.setId(10L);
            return created;
        });
        when(roles.findByAccountIdWithRole(7L)).thenReturn(List.of());

        service.refreshToken("fixture-refresh");

        verify(entityManager).refresh(session, LockModeType.PESSIMISTIC_WRITE);
        assertEquals("TOKEN_ROTATED", session.getRevokeReason());
        verify(sessions).save(argThat(created -> created.getAccount().getId().equals(7L)
                && created.getDeviceInfo().equals("fixture-device")));
        verify(jwt).generateAccessToken(eq(7L), eq("qa_auth_fixture"), eq(List.of()), eq(10L));
    }
}
