package org.booklore.grimmlink.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * High-priority security chain that prevents recursive AccessDenied on error dispatch.
 * <p>
 * Problem: when a request fails authentication/authorization, Spring Boot dispatches
 * an ERROR dispatch to {@code /error}. If that dispatch itself is denied by a security
 * chain, it produces a recursive "Unable to handle the Spring Security Exception
 * because the response is already committed" cycle.
 * <p>
 * This chain runs at {@code @Order(-1)} — before all other chains — and matches ONLY
 * ERROR dispatches and the {@code /error} endpoint itself. It permits all such requests
 * without authentication, ensuring the error page is always reachable.
 * <p>
 * Fork-isolated: entirely under {@code org.booklore.grimmlink.**}. No changes to
 * upstream {@code SecurityConfig.java}.
 */
@Slf4j
@Configuration
public class GrimmLinkErrorDispatchSecurityConfig {

    @Bean
    @Order(-1)
    public SecurityFilterChain grimmLinkErrorDispatchChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(this::matchErrorDispatch)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        ;

        log.debug("GrimmLinkErrorDispatchChain registered at @Order(-1) — ERROR dispatches and /error are permitted");

        return http.build();
    }

    /**
     * Returns {@code true} for ERROR dispatches (any path) and for REQUEST dispatches
     * whose URI is {@code /error}. This ensures:
     * <ul>
     *   <li>Error page rendering never triggers authentication</li>
     *   <li>Direct access to {@code /error} is open (harmless — it's the error page)</li>
     *   <li>Normal API requests still pass through to the correct chain</li>
     * </ul>
     */
    boolean matchErrorDispatch(HttpServletRequest request) {
        DispatcherType dt = request.getDispatcherType();

        if (dt == DispatcherType.ERROR) {
            boolean authenticated = SecurityContextHolder.getContext().getAuthentication() != null
                    && SecurityContextHolder.getContext().getAuthentication().isAuthenticated();
            log.debug("GrimmLinkErrorDispatchChain matched: dispatcherType=ERROR, uri={}, method={}, authenticated={}",
                    request.getRequestURI(), request.getMethod(), authenticated);
            return true;
        }

        if ("/error".equals(request.getRequestURI())) {
            log.debug("GrimmLinkErrorDispatchChain matched: uri=/error, dispatcherType={}, method={}",
                    request.getDispatcherType().name(), request.getMethod());
            return true;
        }

        return false;
    }
}
