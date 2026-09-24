package com.lyh.liuaiagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AiRateLimitConfig implements WebMvcConfigurer {
    private final AiRateLimitInterceptor interceptor;

    public AiRateLimitConfig(AiRateLimitInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/ai/love_app/chat/**", "/ai/manus/chat/**");
    }
}
