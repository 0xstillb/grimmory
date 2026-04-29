package org.booklore.config.security.filter;

import jakarta.servlet.FilterChain;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoreaderUserEntity;
import org.booklore.repository.KoreaderUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KoreaderAuthFilterTest {

    @Mock
    private KoreaderUserRepository koreaderUserRepository;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingHeaders_setsHelpfulFailureReasonForKoreaderApi() throws Exception {
        KoreaderAuthFilter filter = new KoreaderAuthFilter(koreaderUserRepository);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/koreader/users/auth");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals("Missing KOReader authentication headers", request.getAttribute("auth.failure.reason"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void invalidCredentials_setsFailureReason() throws Exception {
        KoreaderAuthFilter filter = new KoreaderAuthFilter(koreaderUserRepository);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/koreader/users/auth");
        request.addHeader("x-auth-user", "reader");
        request.addHeader("x-auth-key", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        KoreaderUserEntity user = new KoreaderUserEntity();
        user.setUsername("reader");
        user.setPasswordMD5("expected");
        when(koreaderUserRepository.findByUsername("reader")).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, response, filterChain);

        assertEquals("Invalid KOReader credentials", request.getAttribute("auth.failure.reason"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void validCredentials_authenticateUser() throws Exception {
        KoreaderAuthFilter filter = new KoreaderAuthFilter(koreaderUserRepository);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/koreader/users/auth");
        request.addHeader("x-auth-user", "reader");
        request.addHeader("x-auth-key", "expected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        BookLoreUserEntity linkedUser = BookLoreUserEntity.builder().id(11L).build();
        KoreaderUserEntity user = new KoreaderUserEntity();
        user.setUsername("reader");
        user.setPasswordMD5("expected");
        user.setSyncEnabled(true);
        user.setBookLoreUser(linkedUser);
        when(koreaderUserRepository.findByUsername("reader")).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getAttribute("auth.failure.reason"));
    }
}
