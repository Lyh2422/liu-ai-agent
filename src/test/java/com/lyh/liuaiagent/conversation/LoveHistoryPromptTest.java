package com.lyh.liuaiagent.conversation;

import com.lyh.liuaiagent.app.LoveApp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class LoveHistoryPromptTest {
    @Test void restoredHistoryIsIncludedOnceInActualModelPrompt() {
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("你叫小林"))))));
        LoveApp love = new LoveApp(model);
        assertEquals("你叫小林", love.chatWithHistory("我叫什么？", List.of(new UserMessage("我叫小林"), new AssistantMessage("你好小林"))).blockLast());
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).stream(prompt.capture());
        var messages = prompt.getValue().getInstructions().stream().filter(message -> message.getMessageType() != MessageType.SYSTEM).map(Message::getText).toList();
        assertEquals(List.of("我叫小林", "你好小林", "我叫什么？"), messages);
    }
}
