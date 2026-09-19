package com.lyh.liuaiagent.controller;

import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.conversation.Conversation;
import com.lyh.liuaiagent.conversation.ConversationChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
public class AiController {
    private static final String UTF8_EVENT_STREAM = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8";
    private final ConversationChatService chat;
    public AiController(ConversationChatService chat) { this.chat = chat; }

    @ModelAttribute
    public void streamingHeaders(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
    }

    public record ChatRequest(@NotBlank String chatId, @NotBlank @Size(max = 10000) String message) {}

    @PostMapping(value = "/love_app/chat/sse", produces = UTF8_EVENT_STREAM)
    public Flux<ServerSentEvent<String>> love(@AuthenticationPrincipal UserAccount user, @Valid @RequestBody ChatRequest request) {
        return chat.stream(user.getId(), request.chatId(), Conversation.AppType.LOVE, request.message().strip());
    }

    @PostMapping(value = "/manus/chat", produces = UTF8_EVENT_STREAM)
    public Flux<ServerSentEvent<String>> manus(@AuthenticationPrincipal UserAccount user, @Valid @RequestBody ChatRequest request) {
        return chat.stream(user.getId(), request.chatId(), Conversation.AppType.MANUS, request.message().strip());
    }

    // 保留原 GET 路由，但也必须使用当前用户已经创建的会话。
    @GetMapping(value = {"/love_app/chat/sse", "/love_app/chat/server_sent_event", "/love_app/chat/sse_emitter"},
            produces = UTF8_EVENT_STREAM)
    public Flux<ServerSentEvent<String>> loveLegacy(@AuthenticationPrincipal UserAccount user, @Valid @ModelAttribute ChatRequest request) {
        return love(user, request);
    }

    @GetMapping(value = "/manus/chat", produces = UTF8_EVENT_STREAM)
    public Flux<ServerSentEvent<String>> manusLegacy(@AuthenticationPrincipal UserAccount user, @Valid @ModelAttribute ChatRequest request) {
        return manus(user, request);
    }

    @GetMapping("/love_app/chat/sync")
    public String sync(@AuthenticationPrincipal UserAccount user, @Valid @ModelAttribute ChatRequest request) {
        return love(user, request).filter(event -> "delta".equals(event.event()) || "error".equals(event.event()))
                .map(ServerSentEvent::data).collectList().map(parts -> String.join("", parts)).block();
    }
}
