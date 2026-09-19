package com.lyh.liuaiagent.auth.bootstrap;

import com.lyh.liuaiagent.auth.config.AuthProperties;
import com.lyh.liuaiagent.auth.service.AuthService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AuthService authService;
    private final AuthProperties properties;

    public AdminBootstrap(AuthService authService, AuthProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        authService.bootstrapAdmin(
                properties.getBootstrapAdmin().getUsername(),
                properties.getBootstrapAdmin().getPassword());
    }
}
