package com.lyh.liuaiagent.conversation;

import com.lyh.liuaiagent.app.LoveApp;
import com.lyh.liuaiagent.cache.HotQuestionCacheService;
import com.lyh.liuaiagent.knowledge.KnowledgeIndex;
import com.lyh.liuaiagent.auth.config.*;
import com.lyh.liuaiagent.auth.controller.AuthExceptionHandler;
import com.lyh.liuaiagent.auth.filter.JwtAuthenticationFilter;
import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.auth.repository.UserAccountRepository;
import com.lyh.liuaiagent.auth.service.JwtService;
import com.lyh.liuaiagent.controller.AiController;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import reactor.core.publisher.Flux;
import java.time.Duration;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

class ConversationSecurityStreamTest {
    static KnowledgeIndex mockKnowledge() {
        var index = mock(KnowledgeIndex.class);
        when(index.cacheVersion()).thenReturn("test-v1");
        return index;
    }
    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
            ConversationController.class, AiController.class, AuthExceptionHandler.class})
    static class WebConfig {
        @Bean AuthProperties authProperties() {
            var properties = new AuthProperties();
            properties.getJwt().setSecret("history-integration-test-secret-at-least-32-characters");
            return properties;
        }
        @Bean UserAccountRepository users() { return mock(UserAccountRepository.class); }
        @Bean ConversationStore store() { return mock(ConversationStore.class); }
        final LoveApp love = mock(LoveApp.class);
        @Bean ConversationChatService chat(ConversationStore store) {
            return new ConversationChatService(store, love, new ToolCallback[0], mock(ChatModel.class), new HotQuestionCacheService(), mockKnowledge());
        }
    }

    @Test void authenticatedStreamSurvivesAsyncDispatchAndAnonymousHistoryIsRejected() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(WebConfig.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
            mvc.perform(get("/ai/conversations").param("appType", "LOVE")).andExpect(status().isUnauthorized());
            var user = new UserAccount();
            user.setId(1L); user.setUsername("history-test");
            when(context.getBean(UserAccountRepository.class).findById(1L)).thenReturn(Optional.of(user));
            var token = context.getBean(JwtService.class).createToken(user);
            var store = context.getBean(ConversationStore.class);
            when(store.begin(1L, "mine", Conversation.AppType.LOVE, "你好"))
                    .thenReturn(new ConversationStore.StartedTurn(1L, List.of()));
            when(context.getBean(WebConfig.class).love.chatWithHistory("你好", List.of()))
                    .thenReturn(Flux.just("回复已保存").delayElements(Duration.ofMillis(10)));
            var result = mvc.perform(post("/ai/love_app/chat/sse")
                    .header("Authorization", "Bearer " + token).contentType("application/json")
                    .content("{\"chatId\":\"mine\",\"message\":\"你好\"}"))
                    .andExpect(request().asyncStarted()).andReturn();
            result.getAsyncResult(5000);
            mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store, no-transform"))
                    .andExpect(header().string("X-Accel-Buffering", "no"))
                    .andExpect(content().string(containsString("event:done")));
            verify(store).append(1L, "回复已保存");
            verify(store).finish(1L, ChatTurn.Status.COMPLETED);
        }
    }
}
