package com.lyh.liuaiagent.conversation;

import com.lyh.liuaiagent.auth.controller.AuthExceptionHandler;
import com.lyh.liuaiagent.auth.exception.NotFoundException;
import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.controller.AiController;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConversationControllerTest {
    MockMvc mvc;
    ConversationStore store;
    ConversationChatService chat;

    @BeforeEach void setup() {
        store = mock(ConversationStore.class);
        chat = mock(ConversationChatService.class);
        var user = new UserAccount();
        user.setId(17L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new ConversationController(store), new AiController(chat))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new AuthExceptionHandler()).build();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void creationUsesAuthenticatedOwnerAndValidatesBody() throws Exception {
        var conversation = new Conversation();
        conversation.setUserId(17L);
        conversation.setAppType(Conversation.AppType.LOVE);
        when(store.create(17L, Conversation.AppType.LOVE)).thenReturn(conversation);
        mvc.perform(post("/ai/conversations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"appType\":\"LOVE\",\"userId\":999}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(17));
        mvc.perform(post("/ai/conversations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(store, times(1)).create(anyLong(), any());
    }

    @Test void historyAndAllLegacyRoutesEnforceOwnership() throws Exception {
        when(store.detail(17L, "foreign")).thenThrow(new NotFoundException("会话不存在"));
        when(chat.stream(eq(17L), eq("foreign"), any(), anyString())).thenThrow(new NotFoundException("会话不存在"));
        mvc.perform(get("/ai/conversations/foreign")).andExpect(status().isNotFound());
        for (String path : List.of("/ai/love_app/chat/sse", "/ai/love_app/chat/server_sent_event", "/ai/love_app/chat/sse_emitter", "/ai/love_app/chat/sync", "/ai/manus/chat")) {
            mvc.perform(get(path).param("chatId", "foreign").param("message", "hello")).andExpect(status().isNotFound());
        }
        for (String path : List.of("/ai/love_app/chat/sse", "/ai/manus/chat")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"chatId\":\"foreign\",\"message\":\"hello\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test void blankMessagesAreRejectedBeforeStartingGeneration() throws Exception {
        mvc.perform(post("/ai/love_app/chat/sse").contentType(MediaType.APPLICATION_JSON)
                .content("{\"chatId\":\"abc\",\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/ai/manus/chat").param("message", "hi")).andExpect(status().isBadRequest());
        verifyNoInteractions(chat);
    }
}
