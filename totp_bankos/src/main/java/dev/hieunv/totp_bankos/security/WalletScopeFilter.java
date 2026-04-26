package dev.hieunv.totp_bankos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs after JwtAuthFilter.
 *
 * Wallet-scoped paths (everything under /api/wallets/{id}/*, /api/transfers/*, etc.)
 * MUST carry a wallet-scoped token.  If the request reaches a protected route but
 * AppSecurityContext has no walletId, we short-circuit with 403.
 */
@Component
@Slf4j
public class WalletScopeFilter extends OncePerRequestFilter {

    // paths that require a wallet-scoped token
    private static final String[] WALLET_SCOPED_PREFIXES = {
            "/api/transfers",
            "/api/cashin",
            "/api/cashout",
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (requiresWalletScope(path) && AppSecurityContext.getWalletId() == null) {
            log.warn("Wallet-scoped path {} reached without a wallet token", path);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"error\":\"Wallet token required. Activate a wallet first.\"}"
            );
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean requiresWalletScope(String path) {
        for (String prefix : WALLET_SCOPED_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }
}