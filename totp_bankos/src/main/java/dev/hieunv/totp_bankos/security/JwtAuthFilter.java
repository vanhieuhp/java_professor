package dev.hieunv.totp_bankos.security;

import dev.hieunv.totp_bankos.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        try {
            String token = extractToken(request);

            if (token != null && jwtService.isTokenValid(token)) {
                populateContext(token);
            }

            chain.doFilter(request, response);

        } finally {
            // always clear thread-local state to avoid leaking between requests
            AppSecurityContext.clear();
        }
    }

    // ── private helpers ───────────────────────────────────────

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void populateContext(String token) {
        Long userId   = jwtService.extractUserId(token);
        Long walletId = jwtService.extractWalletId(token);    // null for pre-wallet tokens
        List<String> permissions = jwtService.extractPermissions(token); // null for pre-wallet

        // populate our thread-local context
        AppSecurityContext.setUserId(userId);
        AppSecurityContext.setWalletId(walletId);
        AppSecurityContext.setPermissions(permissions);

        // also populate Spring's SecurityContext so @PreAuthorize / SecurityConfig work
        List<SimpleGrantedAuthority> authorities = permissions == null
                ? List.of()
                : permissions.stream()
                  .map(SimpleGrantedAuthority::new)
                  .toList();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        log.debug("Authenticated userId={} walletId={} permissions={}",
                userId, walletId, permissions == null ? "none" : permissions.size());
    }
}