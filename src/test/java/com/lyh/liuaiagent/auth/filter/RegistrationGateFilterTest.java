package com.lyh.liuaiagent.auth.filter;

import com.lyh.liuaiagent.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationGateFilterTest {
    @Test
    void rejectsRegistrationWhenDisabled() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.setRegistrationEnabled(false);
        RegistrationGateFilter filter = new RegistrationGateFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/register");
        request.setServletPath("/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("关闭公开注册"));
    }

    @Test
    void allowsRegistrationWhenEnabled() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.setRegistrationEnabled(true);
        RegistrationGateFilter filter = new RegistrationGateFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/register");
        request.setServletPath("/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals(request, chain.getRequest());
    }
}
