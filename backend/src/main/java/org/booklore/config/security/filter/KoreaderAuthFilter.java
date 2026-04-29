package org.booklore.config.security.filter;

import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.repository.KoreaderUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
@FilterRegistration(enabled = false)
public class KoreaderAuthFilter extends OncePerRequestFilter {

    private final KoreaderUserRepository koreaderUserRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String username = request.getHeader("x-auth-user");
        String key = request.getHeader("x-auth-key");
        boolean koreaderApiRequest = request.getRequestURI() != null && request.getRequestURI().startsWith("/api/koreader/");

        if (username == null || key == null) {
            if (koreaderApiRequest) {
                request.setAttribute("auth.failure.reason", "Missing KOReader authentication headers");
            }
            chain.doFilter(request, response);
            return;
        }

        var user = koreaderUserRepository.findByUsername(username).orElse(null);
        if (user == null) {
            log.info("KOReader user not found for username '{}'", username);
            request.setAttribute("auth.failure.reason", "Invalid KOReader credentials");
            chain.doFilter(request, response);
            return;
        }

        if (user.getPasswordMD5() == null || !user.getPasswordMD5().equalsIgnoreCase(key)) {
            log.info("KOReader credentials did not match for username '{}'", username);
            request.setAttribute("auth.failure.reason", "Invalid KOReader credentials");
            chain.doFilter(request, response);
            return;
        }

        Long bookLoreUserId = null;
        if (user.getBookLoreUser() != null) {
            bookLoreUserId = user.getBookLoreUser().getId();
        }

        UserDetails userDetails = new KoreaderUserDetails(
                user.getUsername(),
                user.getPasswordMD5(),
                user.isSyncEnabled(),
                user.isSyncWithBookloreReader(),
                bookLoreUserId,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.removeAttribute("auth.failure.reason");

        chain.doFilter(request, response);
    }
}
