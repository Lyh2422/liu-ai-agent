package com.lyh.liuaiagent.auth.filter;

import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.auth.repository.UserAccountRepository;
import com.lyh.liuaiagent.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserAccountRepository repository;

    public JwtAuthenticationFilter(JwtService jwtService, UserAccountRepository repository) {
        this.jwtService = jwtService;
        this.repository = repository;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        // SSE 完成时会再次异步分派；无状态认证需要在该分派中重新建立用户身份。
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parseToken(header.substring(7));
                Long userId = Long.valueOf(claims.getSubject());
                UserAccount user = repository.findById(userId).orElse(null);
                if (user != null && user.isEnabled()) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
