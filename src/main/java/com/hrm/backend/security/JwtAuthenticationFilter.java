package com.hrm.backend.security;

import com.hrm.backend.exception.AuthException;
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
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;

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
                
                if (!userDetails.isEnabled()) {
                    throw new AuthException("AUTH_ACCOUNT_DISABLED", "Account is disabled", HttpStatus.FORBIDDEN.value());
                }
                if (!userDetails.isAccountNonLocked()) {
                    throw new AuthException("AUTH_ACCOUNT_LOCKED", "Account is locked", HttpStatus.FORBIDDEN.value());
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredJwtException ex) {
            // we catch specific JWT exceptions to let GlobalExceptionHandler handle it if we want, 
            // but filters run before DispatcherServlet, so we might need to handle it here or let Spring Security entry point handle it.
            // For simplicity, we just clear context and let Spring Security return 401.
            request.setAttribute("exception", ex);
        } catch (JwtException ex) {
            request.setAttribute("exception", ex);
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
}
