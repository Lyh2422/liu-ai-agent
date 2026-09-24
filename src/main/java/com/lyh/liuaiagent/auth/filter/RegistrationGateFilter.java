package com.lyh.liuaiagent.auth.filter;

import com.lyh.liuaiagent.auth.config.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Allows public registration to be closed after the invited demo users have signed up. */
@Component
public class RegistrationGateFilter extends OncePerRequestFilter {
    private final AuthProperties properties;

    public RegistrationGateFilter(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return properties.isRegistrationEnabled()
                || !HttpMethod.POST.matches(request.getMethod())
                || !"/auth/register".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":403,\"message\":\"当前已关闭公开注册，请联系管理员\"}");
    }
}
