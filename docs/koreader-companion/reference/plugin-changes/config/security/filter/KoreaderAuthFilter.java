package org.booklore.config.security.filter;

import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.repository.KoreaderUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
@Component
public class KoreaderAuthFilter extends OncePerRequestFilter {

    private final KoreaderUserRepository koreaderUserRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();

        boolean isKoreaderEndpoint = path.startsWith("/api/koreader/");
        boolean isReadingSessionsUploadEndpoint =
                path.equals("/api/v1/reading-sessions")
                        || path.equals("/api/v1/reading-sessions/")
                        || path.startsWith("/api/v1/reading-sessions/batch");

        if (!isKoreaderEndpoint && !isReadingSessionsUploadEndpoint) {
            chain.doFilter(request, response);
            return;
        }
        
        String username = request.getHeader("x-auth-user");
        String key = request.getHeader("x-auth-key");
        
        // Process only if KOReader headers are present
        if (username != null && key != null) {
            koreaderUserRepository.findByUsername(username).ifPresent(user -> {
                if (credentialsMatch(user.getPasswordMD5(), key)) {
                    Long bookLoreUserId = user.getBookLoreUser() != null ? user.getBookLoreUser().getId() : null;
                    
                    // Create KoreaderUserDetails as principal
                    KoreaderUserDetails koreaderUserDetails = new KoreaderUserDetails(
                        user.getUsername(),
                        user.getPasswordMD5(),
                        user.isSyncEnabled(),
                        user.isSyncWithBookloreReader(),
                        bookLoreUserId,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        koreaderUserDetails, 
                        null, 
                        koreaderUserDetails.getAuthorities()
                    );

                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            });
        }
        // If headers are missing, silently skip - JWT auth filter will handle it

        chain.doFilter(request, response);
    }

    private boolean credentialsMatch(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }

        byte[] expectedBytes = expected.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }
}
