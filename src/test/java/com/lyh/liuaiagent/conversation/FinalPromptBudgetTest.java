package com.lyh.liuaiagent.conversation;

import com.lyh.liuaiagent.app.LoveApp;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinalPromptBudgetTest {
    private ChatModel model() {
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("收到"))))));
        return model;
    }

    @Test void actualModelPromptKeepsSystemAndCurrentMessageWhileDroppingCompleteOldPairs() {
        ChatModel model = model();
        var love = new LoveApp(model, false, 3000, 128);
        List<Message> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(new UserMessage("旧问题" + i + "中".repeat(300)));
            history.add(new AssistantMessage("旧回答" + i + "文".repeat(300)));
        }
        String current = "现在我想先修复关系 {保留原文}";
        love.chatWithHistory(current, history).blockLast();
        var capture = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).stream(capture.capture());
        List<Message> sent = capture.getValue().getInstructions();
        assertTrue(sent.stream().anyMatch(m -> m.getMessageType() == MessageType.SYSTEM && m.getText().contains("准确性边界")));
        assertEquals(current, sent.getLast().getText());
        assertTrue(sent.stream().mapToInt(new ContextTokenEstimator()::estimate).sum() <= 2872);
        assertFalse(sent.stream().anyMatch(m -> m.getText().startsWith("旧问题0")));
        assertEquals(1, sent.stream().filter(m -> m.getMessageType() == MessageType.USER).count()
                - sent.stream().filter(m -> m.getMessageType() == MessageType.ASSISTANT).count());
    }

    @Test void oversizedRagAugmentationIsCheckedAfterAdvisorAndCannotReachModel() {
        ChatModel model = model();
        var client = ChatClient.builder(model).defaultSystem("保留系统规则")
                .defaultAdvisors(new AugmentingAdvisor(), new FinalPromptBudgetAdvisor(1000, 64)).build();
        String original = "上次吵架后该怎么办";
        client.prompt().user(original).advisors(spec -> spec.param(FinalPromptBudgetAdvisor.CURRENT_MESSAGE, original))
                .stream().content().blockLast();
        var capture = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).stream(capture.capture());
        String text = capture.getValue().getContents();
        assertTrue(text.contains(original));
        assertTrue(text.contains("不得编造引用"));
        assertFalse(text.contains("资料".repeat(100)));
        assertTrue(capture.getValue().getInstructions().stream().mapToInt(new ContextTokenEstimator()::estimate).sum() <= 936);
    }

    @Test void impossibleCurrentMessageFailsWithoutCallingModel() {
        ChatModel model = model();
        var love = new LoveApp(model, false, 1500, 128);
        assertThrows(InvalidConversationRequestException.class,
                () -> love.chatWithHistory("长".repeat(2000), List.of()).blockLast());
        verify(model, never()).stream(any(Prompt.class));
    }

    private static class AugmentingAdvisor implements StreamAroundAdvisor {
        @Override public String getName() { return "test-rag"; }
        @Override public int getOrder() { return 0; }
        @Override public Flux<AdvisedResponse> aroundStream(AdvisedRequest request, StreamAroundAdvisorChain chain) {
            return chain.nextAroundStream(AdvisedRequest.from(request)
                    .userText("参考资料：" + "资料".repeat(5000) + "\n用户问题：" + request.userText()).build());
        }
    }
}
