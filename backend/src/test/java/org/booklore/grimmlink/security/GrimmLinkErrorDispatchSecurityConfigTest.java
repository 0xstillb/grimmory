package org.booklore.grimmlink.security;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link GrimmLinkErrorDispatchSecurityConfig}.
 * <p>
 * Verifies that the error-dispatch security matcher correctly:
 * <ul>
 *   <li>Permits ERROR dispatches (any path)</li>
 *   <li>Permits REQUEST dispatches to {@code /error}</li>
 *   <li>Does NOT match normal API requests (they fall through to other chains)</li>
 *   <li>Does NOT match ASYNC dispatches (handled by {@code jwtApiSecurityChain})</li>
 * </ul>
 */
class GrimmLinkErrorDispatchSecurityConfigTest {

    private GrimmLinkErrorDispatchSecurityConfig config;

    @BeforeEach
    void setUp() {
        config = new GrimmLinkErrorDispatchSecurityConfig();
        SecurityContextHolder.clearContext();
    }

    // ── ERROR dispatches ──────────────────────────────────────────────

    @Test
    void errorDispatchToError_isPermitted() {
        MockHttpServletRequest request = request(DispatcherType.ERROR, "/error");
        assertTrue(config.matchErrorDispatch(request),
                "ERROR dispatch to /error should be matched (permitted)");
    }

    @Test
    void errorDispatchToApiPath_isPermitted() {
        MockHttpServletRequest request = request(DispatcherType.ERROR, "/api/grimmlink/v1/auth");
        assertTrue(config.matchErrorDispatch(request),
                "ERROR dispatch to any path should be matched (permitted)");
    }

    @Test
    void errorDispatchToOtherPath_isPermitted() {
        MockHttpServletRequest request = request(DispatcherType.ERROR, "/api/v1/books");
        assertTrue(config.matchErrorDispatch(request),
                "ERROR dispatch to non-GrimmLink paths should be matched (permitted)");
    }

    // ── REQUEST dispatches ────────────────────────────────────────────

    @Test
    void requestToError_isPermitted() {
        MockHttpServletRequest request = request(DispatcherType.REQUEST, "/error");
        assertTrue(config.matchErrorDispatch(request),
                "REQUEST dispatch to /error should be matched (permitted)");
    }

    @Test
    void requestToGrimmLinkApi_isNotMatched() {
        MockHttpServletRequest request = request(DispatcherType.REQUEST, "/api/grimmlink/v1/auth");
        assertFalse(config.matchErrorDispatch(request),
                "Normal REQUEST to GrimmLink API should NOT match (falls through to GrimmLink chain)");
    }

    @Test
    void requestToOtherApi_isNotMatched() {
        MockHttpServletRequest request = request(DispatcherType.REQUEST, "/api/v1/books/123");
        assertFalse(config.matchErrorDispatch(request),
                "Normal REQUEST to other API should NOT match (falls through to normal chains)");
    }

    @Test
    void requestToRoot_isNotMatched() {
        MockHttpServletRequest request = request(DispatcherType.REQUEST, "/");
        assertFalse(config.matchErrorDispatch(request),
                "Normal REQUEST to root should NOT match");
    }

    @Test
    void requestToStaticResource_isNotMatched() {
        MockHttpServletRequest request = request(DispatcherType.REQUEST, "/index.html");
        assertFalse(config.matchErrorDispatch(request),
                "Normal REQUEST to static resource should NOT match");
    }

    @Test
    void requestToKomgaApi_isNotMatched() {
        MockHttpServletRequest request = request(DispatcherType.REQUEST, "/komga/api/v1/series");
        assertFalse(config.matchErrorDispatch(request),
                "Normal REQUEST to Komga API should NOT match");
    }

    // ── Other dispatcher types ────────────────────────────────────────

    @Test
    void asyncDispatch_isNotMatched() {
        MockHttpServletRequest request = request(DispatcherType.ASYNC, "/api/grimmlink/v1/books");
        assertFalse(config.matchErrorDispatch(request),
                "ASYNC dispatch should NOT match (handled by jwtApiSecurityChain)");
    }

    @Test
    void forwardDispatch_isNotMatchedUnlessError() {
        MockHttpServletRequest request = request(DispatcherType.FORWARD, "/api/grimmlink/v1/books");
        assertFalse(config.matchErrorDispatch(request),
                "FORWARD dispatch should NOT match (not ERROR type)");
    }

    @Test
    void includeDispatch_isNotMatchedUnlessError() {
        MockHttpServletRequest request = request(DispatcherType.INCLUDE, "/api/grimmlink/v1/books");
        assertFalse(config.matchErrorDispatch(request),
                "INCLUDE dispatch should NOT match (not ERROR type)");
    }

    // ── Security context logging (no-op safety) ───────────────────────

    @Test
    void matcherDoesNotThrow_whenNoSecurityContext() {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = request(DispatcherType.ERROR, "/error");
        // Should not throw even with no authentication set
        assertTrue(config.matchErrorDispatch(request));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static MockHttpServletRequest request(DispatcherType dispatcherType, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setDispatcherType(dispatcherType);
        request.setRequestURI(uri);
        request.setMethod("GET");
        return request;
    }
}
