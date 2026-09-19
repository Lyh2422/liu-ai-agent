package com.lyh.liuaiagent.knowledge;

import com.lyh.liuaiagent.auth.config.*;
import com.lyh.liuaiagent.auth.controller.AuthExceptionHandler;
import com.lyh.liuaiagent.auth.filter.JwtAuthenticationFilter;
import com.lyh.liuaiagent.auth.model.*;
import com.lyh.liuaiagent.auth.repository.UserAccountRepository;
import com.lyh.liuaiagent.auth.service.JwtService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KnowledgeSecurityTest {
    @Configuration @EnableWebMvc
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, KnowledgeController.class,
            KnowledgeExceptionHandler.class, AuthExceptionHandler.class})
    static class Config {
        @Bean AuthProperties properties() {
            var properties = new AuthProperties(); properties.getJwt().setSecret("knowledge-test-secret-at-least-32-characters"); return properties;
        }
        @Bean UserAccountRepository users() { return mock(UserAccountRepository.class); }
        @Bean KnowledgeDocumentStore store() { return mock(KnowledgeDocumentStore.class); }
        @Bean KnowledgeManagementService management() { return mock(KnowledgeManagementService.class); }
    }
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    KnowledgeDocumentStore store;
    KnowledgeManagementService management;
    UserAccount user;
    String token;
    final String base = "/admin/knowledge/documents";

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        store = context.getBean(KnowledgeDocumentStore.class); management = context.getBean(KnowledgeManagementService.class);
        user = new UserAccount(); user.setId(42L); user.setUsername("test-admin"); user.setRole(UserRole.ADMIN);
        when(context.getBean(UserAccountRepository.class).findById(42L)).thenReturn(Optional.of(user));
        token = context.getBean(JwtService.class).createToken(user);
    }
    @AfterEach void close() { context.close(); }
    List<MockHttpServletRequestBuilder> allRoutes() {
        return List.of(get(base), get(base + "/one"), multipart(base).file(KnowledgeManagementTest.file("test.md", "知识内容")),
                put(base + "/one").contentType("application/json").content("{\"version\":0,\"title\":\"标题\",\"content\":\"正文\"}"), delete(base + "/one").param("version", "0"));
    }

    @Test void anonymousAndOrdinaryUsersCannotAccessAnyManagementEndpoint() throws Exception {
        for (var request : allRoutes()) mvc.perform(request).andExpect(status().isUnauthorized());
        // 已签发的管理员 token 也不能绕过数据库中的最新角色。
        user.setRole(UserRole.USER);
        for (var request : allRoutes()) mvc.perform(request.header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        verifyNoInteractions(management);
        verify(store, never()).all();
    }

    @Test void adminCanReadUploadEditAndDeleteUsingAuthenticatedIdentity() throws Exception {
        var document = new KnowledgeDocument(); document.setId("one"); document.setVersion(0L);
        document.setTitle("标题"); document.setFilename("test.md"); document.setContent("正文");
        when(store.all()).thenReturn(List.of(document)); when(store.get("one")).thenReturn(document);
        when(management.upload(any(), eq(42L))).thenReturn(document);
        when(management.update("one", 0, "标题", "正文", 42L)).thenReturn(document);
        int[] statuses = {200, 200, 201, 200, 204}; int i = 0;
        for (var request : allRoutes()) mvc.perform(request.header("Authorization", "Bearer " + token)).andExpect(status().is(statuses[i++]));
        verify(management).upload(any(), eq(42L)); verify(management).delete("one", 0);
        verify(management).update("one", 0, "标题", "正文", 42L);
    }

    @Test void malformedEditsAndMissingDeleteVersionAreRejected() throws Exception {
        for (String json : List.of("{}", "{\"version\":0,\"title\":\" \",\"content\":\"正文\"}", "{\"version\":-1,\"title\":\"标题\",\"content\":\"正文\"}")) {
            mvc.perform(put(base + "/one").header("Authorization", "Bearer " + token).contentType("application/json").content(json)).andExpect(status().isBadRequest());
        }
        mvc.perform(delete(base + "/one").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        verifyNoInteractions(management);
    }

    @Test void indexFailureReturnsUsefulErrorWithoutReportingSuccess() throws Exception {
        when(management.update(anyString(), anyLong(), anyString(), anyString(), anyLong()))
                .thenThrow(new KnowledgeManagementService.IndexUpdateException("知识索引更新失败，本次修改未保存，请稍后重试", new RuntimeException()));
        mvc.perform(allRoutes().get(3).header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("知识索引更新失败，本次修改未保存，请稍后重试"));
    }
}
