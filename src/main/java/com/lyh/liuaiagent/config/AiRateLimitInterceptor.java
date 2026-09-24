package com.lyh.liuaiagent.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A small in-memory guardrail for friend/demo deployments. */
@Component
public class AiRateLimitInterceptor implements HandlerInterceptor {
    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int requestsPerMinute;

    @Autowired
    public AiRateLimitInterceptor(
            @Value("${app.ai-rate-limit.requests-per-minute:0}") int requestsPerMinute) {
        this(requestsPerMinute, Clock.systemUTC());
    }

    AiRateLimitInterceptor(int requestsPerMinute, Clock clock) {
        this.requestsPerMinute = Math.max(0, requestsPerMinute);
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (requestsPerMinute == 0) {
            return true;
        }

        String key = clientKey(request);
        long now = clock.millis();
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
        synchronized (window) {
            if (now - window.startedAt >= WINDOW_MILLIS) {
                window.startedAt = now;
                window.requests = 0;
            }
            if (window.requests >= requestsPerMinute) {
                writeRateLimited(response);
                return false;
            }
            window.requests++;
        }
        return true;
    }

    private String clientKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null && !authentication.getName().isBlank()) {
            return "user:" + authentication.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void writeRateLimited(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        response.getWriter().write("{\"status\":429,\"message\":\"AI 请求过于频繁，请稍后再试\"}");
    }

    private static final class Window {
        private long startedAt;
        private int requests;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
