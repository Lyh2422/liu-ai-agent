package com.lyh.liuaiagent.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DashScopeProtocolConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = {"application.yml", "application-prod.yml"})
    void configuredQwenUsesMultimodalRouteAndDecodesText(String resource) throws Exception {
        var environment = new StandardEnvironment();
        for (var source : new YamlPropertySourceLoader().load("protocol", new ClassPathResource(resource))) {
            environment.getPropertySources().addFirst(source);
        }
        var options = new DashScopeChatOptions();
        options.setModel(environment.getProperty("spring.ai.dashscope.chat.options.model"));
        options.setMultiModel(environment.getProperty("spring.ai.dashscope.chat.options.multi-model", Boolean.class));
        assertEquals("qwen3.7-flash", options.getModel());
        assertEquals(Boolean.TRUE, options.getMultiModel());
        var path = new AtomicReference<String>();
        var web = WebClient.builder().exchangeFunction(request -> {
            path.set(request.url().getPath());
            String data = "data:{\"request_id\":\"fixture-request\",\"output\":{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"你好\"}]},\"finish_reason\":\"stop\"}]},\"usage\":{\"input_tokens\":1,\"output_tokens\":1,\"total_tokens\":2}}\n\ndata:[DONE]\n\n";
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "text/event-stream").body(data).build());
        });
        var api = new DashScopeApi("https://example.invalid", "fixture-key", RestClient.builder(), web, new DefaultResponseErrorHandler());
        var model = new DashScopeChatModel(api, options);
        var response = new com.lyh.liuaiagent.app.LoveApp(model).chatWithHistory("你好", java.util.List.of()).collectList().block(Duration.ofSeconds(5));
        assertEquals("/api/v1/services/aigc/multimodal-generation/generation", path.get());
        assertTrue(response.contains("你好"));
        var agent = new com.lyh.liuaiagent.agent.LiuManus(new org.springframework.ai.tool.ToolCallback[0], model);
        path.set(null);
        var agentResponse = org.springframework.ai.chat.client.ChatClient.create(model)
                .prompt(new Prompt("你好", agent.getChatOptions())).stream().content()
                .collectList().block(Duration.ofSeconds(5));
        assertEquals("/api/v1/services/aigc/multimodal-generation/generation", path.get());
        assertTrue(agentResponse.contains("你好"));
    }
}
