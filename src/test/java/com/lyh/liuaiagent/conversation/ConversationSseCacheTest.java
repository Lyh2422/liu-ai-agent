package com.lyh.liuaiagent.conversation;

import com.lyh.liuaiagent.app.LoveApp;
import com.lyh.liuaiagent.cache.HotQuestionCacheService;
import com.lyh.liuaiagent.knowledge.KnowledgeIndex;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationSseCacheTest {
    private final ConversationStore store = mock(ConversationStore.class);
    private final LoveApp love = mock(LoveApp.class);
    private final HotQuestionCacheService cache = new HotQuestionCacheService();
    private final KnowledgeIndex index = mock(KnowledgeIndex.class);
    private final ConversationChatService service = new ConversationChatService(store, love, new ToolCallback[0], mock(ChatModel.class), cache, index);
    private static final String QUESTION = "怎么沟通？";

    private void prepare() {
        when(index.cacheVersion()).thenReturn("v1");
        when(store.begin(anyLong(), anyString(), eq(Conversation.AppType.LOVE), eq(QUESTION)))
                .thenReturn(new ConversationStore.StartedTurn(1L, List.of()));
        when(love.chatWithHistory(eq(QUESTION), anyList())).thenReturn(Flux.just("完整", "回答"));
    }
    private List<org.springframework.http.codec.ServerSentEvent<String>> run(Long user, String conversation) {
        return service.stream(user, conversation, Conversation.AppType.LOVE, QUESTION).collectList().block(Duration.ofSeconds(5));
    }

    @Test void emitsAckBeforeEvenConstructingTheSlowModelPublisher() throws Exception {
        prepare();
        var ack = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var done = new CountDownLatch(1);
        var sawDelta = new AtomicBoolean();
        when(love.chatWithHistory(eq(QUESTION), anyList())).thenAnswer(call -> {
            assertEquals(0, ack.getCount(), "构建模型请求之前必须发出 ack");
            entered.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            return Flux.just("回答");
        });
        var subscription = service.stream(1L, "slow", Conversation.AppType.LOVE, QUESTION).subscribe(event -> {
            if ("ack".equals(event.event())) ack.countDown();
            if ("delta".equals(event.event())) sawDelta.set(true);
        }, error -> done.countDown(), done::countDown);
        try {
            assertTrue(ack.await(1, TimeUnit.SECONDS));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertFalse(sawDelta.get());
            release.countDown();
            assertTrue(done.await(3, TimeUnit.SECONDS));
            assertTrue(sawDelta.get());
        } finally { release.countDown(); subscription.dispose(); }
    }

    @Test void fourthMatchingRequestSkipsModelAndPersistsCachedAnswerInCurrentTurn() {
        prepare();
        for (int i = 0; i < 3; i++) run(1L, "new-" + i);
        when(store.begin(1L, "new-4", Conversation.AppType.LOVE, QUESTION))
                .thenReturn(new ConversationStore.StartedTurn(4L, List.of()));
        var events = run(1L, "new-4");
        assertEquals(List.of("ack", "delta", "done"), events.stream().map(e -> e.event()).toList());
        assertEquals("完整回答", events.get(1).data());
        verify(love, times(3)).chatWithHistory(QUESTION, List.of());
        var order = inOrder(store);
        order.verify(store).begin(1L, "new-4", Conversation.AppType.LOVE, QUESTION);
        order.verify(store).append(4L, "完整回答");
        order.verify(store).finish(4L, ChatTurn.Status.COMPLETED);
    }

    @Test void coalescesSmallProviderChunksBeforePersistingAndDelivering() {
        prepare();
        var events = run(1L, "batched");
        assertEquals(List.of("ack", "delta", "done"), events.stream().map(event -> event.event()).toList());
        assertEquals("完整回答", events.get(1).data());
        verify(store).append(1L, "完整回答");
    }

    @Test void bypassesOldCacheForOtherUsersChangedHistoryAndUpdatedKnowledge() {
        prepare();
        for (int i = 0; i < 3; i++) run(1L, "new-" + i);
        run(2L, "other-user");
        when(store.begin(1L, "with-history", Conversation.AppType.LOVE, QUESTION)).thenReturn(
                new ConversationStore.StartedTurn(8L, List.of(new UserMessage("我已经分手了"))));
        run(1L, "with-history");
        when(index.cacheVersion()).thenReturn("v2");
        run(1L, "updated");
        verify(love, times(6)).chatWithHistory(eq(QUESTION), anyList());
    }

    @Test void neverCachesPartialFailuresOrCancelledAnswers() {
        prepare();
        var key = cache.keyFor(1L, "LOVE", "v1", List.of(), QUESTION);
        for (int i = 0; i < 3; i++) cache.recordQuestion(key);
        when(love.chatWithHistory(eq(QUESTION), anyList()))
                .thenReturn(Flux.concat(Flux.just("部分"), Flux.error(new RuntimeException("failed"))));
        assertEquals(List.of("ack", "delta", "error"), run(1L, "failed").stream().map(e -> e.event()).toList());
        assertTrue(cache.getCachedAnswer(key).isEmpty());
        verify(store, never()).finish(1L, ChatTurn.Status.COMPLETED);
        when(love.chatWithHistory(eq(QUESTION), anyList())).thenReturn(Flux.concat(Flux.just("部分"), Flux.never()));
        service.stream(1L, "cancelled", Conversation.AppType.LOVE, QUESTION).take(2).blockLast(Duration.ofSeconds(5));
        assertTrue(cache.getCachedAnswer(key).isEmpty());
    }

    @Test void doesNotCacheAnAnswerIfKnowledgeChangesDuringGeneration() {
        prepare();
        for (int i = 0; i < 3; i++) cache.recordQuestion(cache.keyFor(1L, "LOVE", "v1", List.of(), QUESTION));
        when(love.chatWithHistory(eq(QUESTION), anyList())).thenReturn(Flux.just("旧版回答")
                .doOnNext(value -> when(index.cacheVersion()).thenReturn("v2")));
        run(1L, "changing");
        assertTrue(cache.getCachedAnswer(cache.keyFor(1L, "LOVE", "v1", List.of(), QUESTION)).isEmpty());
        assertTrue(cache.getCachedAnswer(cache.keyFor(1L, "LOVE", "v2", List.of(), QUESTION)).isEmpty());
    }

    @Test void rejectsUnauthorizedConversationBeforeAnyAckOrCacheLookup() {
        prepare();
        when(store.begin(2L, "not-mine", Conversation.AppType.LOVE, QUESTION))
                .thenThrow(new com.lyh.liuaiagent.auth.exception.NotFoundException("会话不存在"));
        assertThrows(com.lyh.liuaiagent.auth.exception.NotFoundException.class,
                () -> service.stream(2L, "not-mine", Conversation.AppType.LOVE, QUESTION));
        verifyNoInteractions(love);
    }
}
