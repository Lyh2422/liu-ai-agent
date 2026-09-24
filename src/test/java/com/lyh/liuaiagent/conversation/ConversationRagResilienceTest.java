package com.lyh.liuaiagent.conversation;

import com.lyh.liuaiagent.app.LoveApp;
import com.lyh.liuaiagent.cache.HotQuestionCacheService;
import com.lyh.liuaiagent.knowledge.KnowledgeIndex;
import com.lyh.liuaiagent.rag.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import reactor.core.publisher.Flux;
import java.net.SocketException;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 复现实际故障：embedding 断开连接，经真实 RAG Advisor 和 SSE 服务仍完成回答。 */
class ConversationRagResilienceTest {
    @ParameterizedTest
    @ValueSource(strings = {"我最近心情不好 容易和她闹矛盾", "你好"})
    void embeddingFailureStillAllowsPersistedAnswerWithOrWithoutKeywordMatches(String question) {
        var expansion = new LoveAppKeywordExpansionService();
        var corpus = new LoveAppRetrievalCorpus(null, expansion);
        String knowledgeText = "心情不好时先倾诉情绪，发生矛盾时先沟通。";
        corpus.replaceDocuments(List.of(Document.builder().id("emotion").text(knowledgeText).build()));
        var vectors = mock(VectorStore.class);
        when(vectors.similaritySearch(any(SearchRequest.class))).thenThrow(
                new ResourceAccessException("embedding failed", new SocketException("Connection reset")));
        var retriever = new LoveAppHybridDocumentRetriever(vectors, corpus, expansion);
        var model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("我们慢慢聊。"))))));
        var love = new LoveApp(model);
        ReflectionTestUtils.setField(love, "loveAppRagCloudAdvisor", new LoveAppRagCloudAdvisorConfig(retriever).loveAppRagCloudAdvisor());
        var store = mock(ConversationStore.class);
        when(store.begin(1L, "regression", Conversation.AppType.LOVE, question)).thenReturn(
                new ConversationStore.StartedTurn(1L, List.of(new UserMessage("我叫小林"), new AssistantMessage("你好，小林"))));
        var index = mock(KnowledgeIndex.class);
        when(index.cacheVersion()).thenReturn("v1");
        var service = new ConversationChatService(store, love, new ToolCallback[0], model, new HotQuestionCacheService(), index);
        var events = service.stream(1L, "regression", Conversation.AppType.LOVE, question)
                .collectList().block(Duration.ofSeconds(5));
        assertEquals(List.of("ack", "delta", "done"), events.stream().map(e -> e.event()).toList());
        assertEquals("我们慢慢聊。", events.get(1).data());
        verify(store).append(1L, "我们慢慢聊。");
        verify(store).finish(1L, ChatTurn.Status.COMPLETED);
        verify(store, never()).finish(1L, ChatTurn.Status.FAILED);
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).stream(prompt.capture());
        var texts = prompt.getValue().getInstructions().stream().map(Message::getText).toList();
        assertTrue(texts.contains("我叫小林"));
        if (!question.equals("你好")) {
            assertTrue(texts.stream().anyMatch(text -> text.contains(knowledgeText)));
        } else {
            assertTrue(texts.stream().anyMatch(text -> text.contains("本次没有检索到能可靠支持回答的校园知识库资料")));
            assertTrue(texts.stream().anyMatch(text -> text.contains(question)));
        }
        verify(vectors, times(1)).similaritySearch(any(SearchRequest.class));
    }
}
