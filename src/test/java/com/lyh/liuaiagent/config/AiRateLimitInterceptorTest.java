package com.lyh.liuaiagent.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRateLimitInterceptorTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void limitsEachAuthenticatedUserWithinTheMinute() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC);
        AiRateLimitInterceptor interceptor = new AiRateLimitInterceptor(2, clock);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("friend", "n/a", java.util.List.of()));

        assertTrue(interceptor.preHandle(request(), new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(request(), new MockHttpServletResponse(), new Object()));

        MockHttpServletResponse rejected = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request(), rejected, new Object()));
        assertEquals(429, rejected.getStatus());
        assertEquals("60", rejected.getHeader("Retry-After"));
    }

    @Test
    void zeroDisablesTheApplicationLimit() throws Exception {
        AiRateLimitInterceptor interceptor = new AiRateLimitInterceptor(0, Clock.systemUTC());
        assertTrue(interceptor.preHandle(request(), new MockHttpServletResponse(), new Object()));
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("POST", "/ai/love_app/chat/sse");
    }
}
