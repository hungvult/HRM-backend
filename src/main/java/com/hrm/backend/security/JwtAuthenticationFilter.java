package com.hrm.backend.security;

import com.hrm.backend.exception.AuthException;
import com.hrm.backend.entity.AuthSession;
import com.hrm.backend.repository.AuthSessionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;
    private final AuthSessionRepository authSessionRepository;
    private final SecurityErrorResponseWriter errorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                Claims claims = jwtService.validateAndGetClaims(jwt);
                String username = claims.get("username", String.class);
                
                // We could load the full user details from DB to check if they are LOCKED/DISABLED after token creation
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

                // Or just use claims for authorities to be fully stateless, but spec says:
                // "kiểm tra Account ACTIVE" / "403 nếu tài khoản bị khóa sau khi cấp token"
                // So loading UserDetails is safer.
                
                if (!userDetails.isAccountNonLocked()) {
                    throw new AuthException("AUTH_ACCOUNT_LOCKED", "Tài khoản đã bị khóa.", HttpStatus.FORBIDDEN.value());
                }
                if (!userDetails.isEnabled()) {
                    throw new AuthException("AUTH_ACCOUNT_DISABLED", "Tài khoản đã bị vô hiệu hóa.", HttpStatus.FORBIDDEN.value());
                }
                validateSession(claims);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (AuthException ex) {
            SecurityContextHolder.clearContext();
            errorWriter.write(request, response, ex.getStatus(), ex.getCode(), ex.getMessage());
            return;
        } catch (ExpiredJwtException ex) {
            SecurityContextHolder.clearContext();
            errorWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTH_UNAUTHORIZED", "Access token đã hết hạn hoặc không hợp lệ.");
            return;
        } catch (JwtException ex) {
            SecurityContextHolder.clearContext();
            errorWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTH_UNAUTHORIZED", "Access token đã hết hạn hoặc không hợp lệ.");
            return;
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private void validateSession(Claims claims) {
        Long sessionId = jwtService.getSessionIdFromToken(claims);
        Long accountId = jwtService.getAccountIdFromToken(claims);
        if (sessionId == null) {
            throw unauthorizedSession();
        }
        AuthSession session = authSessionRepository.findById(sessionId).orElseThrow(this::unauthorizedSession);
        boolean doesNotBelongToAccount = !session.getAccount().getId().equals(accountId);
        boolean inactive = session.getRevokedAt() != null || !session.getExpiresAt().isAfter(OffsetDateTime.now());
        if (doesNotBelongToAccount || inactive) {
            throw unauthorizedSession();
        }
    }

    private AuthException unauthorizedSession() {
        return new AuthException("AUTH_UNAUTHORIZED", "Phiên đăng nhập đã bị thu hồi hoặc không còn hợp lệ.",
                HttpServletResponse.SC_UNAUTHORIZED);
    }
}
