package dev.hieunv.totp_bankos.config;

import dev.hieunv.totp_bankos.security.JwtAuthFilter;
import dev.hieunv.totp_bankos.security.RbacInterceptor;
import dev.hieunv.totp_bankos.security.WalletScopeFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebSecurity
public class SecurityConfig implements WebMvcConfigurer {

    @Autowired
    private JwtAuthFilter    jwtAuthFilter;
    @Autowired
    private WalletScopeFilter walletScopeFilter;
    @Autowired
    private RbacInterceptor  rbacInterceptor;

    // ── public routes — no token required ────────────────────

    private static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            "/api/auth/refresh",
            "/actuator/health",
            "/error",
    };

    // ── filter chain ──────────────────────────────────────────

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // disable CSRF — we use stateless JWTs
                .csrf(AbstractHttpConfigurer::disable)

                // no HTTP sessions — every request is self-contained
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // route rules
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated()
                )

                // insert our filters before Spring's default username/password filter
                .addFilterBefore(jwtAuthFilter,     UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter (walletScopeFilter, JwtAuthFilter.class);

        return http.build();
    }

    // ── interceptors ──────────────────────────────────────────

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rbacInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_PATHS);
    }

    // ── beans ─────────────────────────────────────────────────

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}