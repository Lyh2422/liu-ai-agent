package com.lyh.liuaiagent.controller;

import com.lyh.liuaiagent.auth.config.AuthProperties;
import com.lyh.liuaiagent.auth.config.SecurityConfig;
import com.lyh.liuaiagent.auth.controller.AuthExceptionHandler;
import com.lyh.liuaiagent.auth.filter.JwtAuthenticationFilter;
import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.auth.repository.UserAccountRepository;
import com.lyh.liuaiagent.auth.service.JwtService;
import com.lyh.liuaiagent.generated.GeneratedFileStore;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GeneratedFileSecurityTest {
    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
            GeneratedFileController.class, AuthExceptionHandler.class})
    static class Config {
        @Bean AuthProperties authProperties() {
            var properties = new AuthProperties();
            properties.getJwt().setSecret("generated-file-test-secret-at-least-32-characters");
            return properties;
        }
        @Bean UserAccountRepository users() { return mock(UserAccountRepository.class); }
        @Bean GeneratedFileStore generatedFiles() { return mock(GeneratedFileStore.class); }
    }

    @Test
    void downloadRequiresLogin() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                    .build();
            String path = "/ai/generated-files/00000000-0000-0000-0000-000000000000";

            mvc.perform(get(path)).andExpect(status().isUnauthorized());

            UserAccount user = new UserAccount();
            user.setId(7L);
            user.setUsername("download-user");
            when(context.getBean(UserAccountRepository.class).findById(7L)).thenReturn(Optional.of(user));
            String token = context.getBean(JwtService.class).createToken(user);
            mvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
        }
    }
}
