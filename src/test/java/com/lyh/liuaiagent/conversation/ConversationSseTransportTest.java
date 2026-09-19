package com.lyh.liuaiagent.conversation;

import com.lyh.liuaiagent.app.LoveApp;
import com.lyh.liuaiagent.cache.HotQuestionCacheService;
import com.lyh.liuaiagent.knowledge.KnowledgeIndex;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import reactor.core.publisher.Flux;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 临时 Tomcat + 本地 HTTP 客户端，验证 ack 真正到达网络，不调用外部模型。 */
class ConversationSseTransportTest {
    @Configuration
    @EnableWebMvc
    @Import(Probe.class)
    static class Config implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
        @Override
        public void extendMessageConverters(List<org.springframework.http.converter.HttpMessageConverter<?>> converters) {
            // 这个极小容器没有 Boot 自动配置，显式匹配应用的 UTF-8 String converter。
            converters.stream().filter(org.springframework.http.converter.StringHttpMessageConverter.class::isInstance)
                    .map(org.springframework.http.converter.StringHttpMessageConverter.class::cast)
                    .forEach(converter -> converter.setDefaultCharset(StandardCharsets.UTF_8));
        }
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch modelEntered = new CountDownLatch(1);
        @Bean TomcatServletWebServerFactory factory() { return new TomcatServletWebServerFactory(0); }
        @Bean DispatcherServlet dispatcherServlet() { return new DispatcherServlet(); }
        @Bean ServletRegistrationBean<DispatcherServlet> registration(DispatcherServlet servlet) {
            return new ServletRegistrationBean<>(servlet, "/");
        }
        @Bean ConversationChatService chat() {
            var store = mock(ConversationStore.class);
            when(store.begin(1L, "probe", Conversation.AppType.LOVE, "问题")).thenReturn(new ConversationStore.StartedTurn(1L, List.of()));
            var love = mock(LoveApp.class);
            when(love.chatWithHistory("问题", List.of())).thenAnswer(call -> {
                modelEntered.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("model gate timed out");
                return Flux.just("模型回答");
            });
            var knowledge = mock(KnowledgeIndex.class);
            when(knowledge.cacheVersion()).thenReturn("test-v1");
            return new ConversationChatService(store, love, new ToolCallback[0], mock(ChatModel.class), new HotQuestionCacheService(), knowledge);
        }
    }
    @RestController
    static class Probe {
        private final ConversationChatService chat;
        Probe(ConversationChatService chat) { this.chat = chat; }
        @GetMapping(value = "/probe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        Flux<ServerSentEvent<String>> probe() { return chat.stream(1L, "probe", Conversation.AppType.LOVE, "问题"); }
    }

    @Test void flushesAckOverHttpWhileModelConstructionIsStillBlocked() throws Exception {
        try (var context = new AnnotationConfigServletWebServerApplicationContext();
             var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
             var readerPool = Executors.newSingleThreadExecutor()) {
            context.register(Config.class); context.refresh();
            var config = context.getBean(Config.class);
            try {
                var request = HttpRequest.newBuilder(URI.create("http://localhost:" + context.getWebServer().getPort() + "/probe"))
                        .timeout(Duration.ofSeconds(5)).build();
                var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).get(5, TimeUnit.SECONDS);
                assertEquals(200, response.statusCode());
                try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    var firstFrame = readerPool.submit(() -> {
                        StringBuilder frame = new StringBuilder();
                        for (String line; (line = reader.readLine()) != null && !line.isEmpty();) frame.append(line).append('\n');
                        return frame.toString();
                    }).get(3, TimeUnit.SECONDS);
                    assertTrue(firstFrame.contains("event:ack"));
                    assertTrue(firstFrame.contains("data:thinking"));
                    assertEquals(1, config.release.getCount());
                    assertTrue(config.modelEntered.await(3, TimeUnit.SECONDS));
                    config.release.countDown();
                    String rest = readerPool.submit(() -> reader.lines().reduce("", (a, b) -> a + b + "\n")).get(5, TimeUnit.SECONDS);
                    assertTrue(rest.contains("event:delta"));
                    assertTrue(rest.contains("模型回答"), () -> "Received: " + rest);
                    assertTrue(rest.contains("event:done"));
                }
            } finally { config.release.countDown(); }
        }
    }
}
