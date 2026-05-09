package org.booklore.config.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoreaderUserEntity;
import org.booklore.repository.KoreaderUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.booklore.config.security.userdetails.KoreaderUserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KoreaderAuthFilterTest {

    @Mock
    private KoreaderUserRepository koreaderUserRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private KoreaderAuthFilter filter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingHeaders_returnsUnauthorized() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/koreader/users/auth");
        when(request.getHeader("x-auth-user")).thenReturn(null);
        when(request.getHeader("x-auth-key")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), any(String.class));
        verifyNoInteractions(koreaderUserRepository);
        verifyNoInteractions(filterChain);
    }

    @Test
    void invalidAuth_returnsUnauthorized() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/koreader/books/by-hash/hash");
        when(request.getHeader("x-auth-user")).thenReturn("reader");
        when(request.getHeader("x-auth-key")).thenReturn("wrong");

        KoreaderUserEntity user = new KoreaderUserEntity();
        user.setUsername("reader");
        user.setPasswordMD5("right");
        when(koreaderUserRepository.findByUsername("reader")).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), any(String.class));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void validAuth_setsSecurityContextAndContinues() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/reading-sessions");
        when(request.getHeader("x-auth-user")).thenReturn("reader");
        when(request.getHeader("x-auth-key")).thenReturn("md5");

        KoreaderUserEntity user = new KoreaderUserEntity();
        user.setUsername("reader");
        user.setPasswordMD5("md5");
        user.setSyncEnabled(true);
        user.setSyncWithBookloreReader(true);
        user.setBookLoreUser(BookLoreUserEntity.builder().id(42L).build());
        when(koreaderUserRepository.findByUsername("reader")).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(eq(request), eq(response));
        KoreaderUserDetails details = (KoreaderUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals("reader", details.getUsername());
    }

    @Test
    void validAuthOnNonTargetPath_isIgnored() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/books/1");
        when(request.getHeader("x-auth-user")).thenReturn("reader");
        when(request.getHeader("x-auth-key")).thenReturn("md5");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(eq(request), eq(response));
        assertEquals(null, SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(response);
        verifyNoInteractions(koreaderUserRepository);
    }
}
