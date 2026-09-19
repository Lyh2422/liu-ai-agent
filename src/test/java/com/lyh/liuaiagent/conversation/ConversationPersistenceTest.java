package com.lyh.liuaiagent.conversation;

import com.lyh.liuaiagent.app.LoveApp;
import com.lyh.liuaiagent.cache.HotQuestionCacheService;
import com.lyh.liuaiagent.knowledge.KnowledgeIndex;
import com.lyh.liuaiagent.auth.exception.ConflictException;
import com.lyh.liuaiagent.auth.exception.NotFoundException;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import reactor.core.publisher.Flux;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversationPersistenceTest {
    static KnowledgeIndex mockKnowledge() {
        var index = mock(KnowledgeIndex.class);
        when(index.cacheVersion()).thenReturn("test-v1");
        return index;
    }
    @TempDir Path temp;

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = ConversationRepository.class)
    @Import(ConversationStore.class)
    static class DatabaseConfig {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.lyh.liuaiagent.conversation");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "update"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }
    }

    AnnotationConfigApplicationContext open() {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> new DriverManagerDataSource(
                "jdbc:h2:file:" + temp.resolve("history") + ";DB_CLOSE_ON_EXIT=FALSE", "sa", ""));
        context.register(DatabaseConfig.class);
        context.refresh();
        return context;
    }

    @Test void diskSurvivesRestartAndInterruptedRepliesAreRecovered() {
        String id;
        try (var context = open()) {
            var store = context.getBean(ConversationStore.class);
            id = store.create(1L, Conversation.AppType.LOVE).getId();
            var first = store.begin(1L, id, Conversation.AppType.LOVE, "我叫小林，周五想表白");
            store.append(first.id(), "小林，我们一起想想。\n先说说你们的关系。");
            store.finish(first.id(), ChatTurn.Status.COMPLETED);
            var pending = store.begin(1L, id, Conversation.AppType.LOVE, "我有些紧张");
            store.append(pending.id(), "紧张是");
        }
        try (var restarted = open()) {
            var store = restarted.getBean(ConversationStore.class);
            store.recoverInterrupted();
            var detail = store.detail(1L, id);
            assertEquals("我叫小林，周五想表白", detail.conversation().getTitle());
            assertEquals(4, detail.messages().size());
            assertEquals("紧张是", detail.messages().get(3).content());
            assertEquals("INTERRUPTED", detail.messages().get(3).status());
            var next = store.begin(1L, id, Conversation.AppType.LOVE, "我叫什么？");
            assertEquals(2, next.history().size());
            assertEquals("我叫小林，周五想表白", next.history().getFirst().getText());
            assertEquals("小林，我们一起想想。\n先说说你们的关系。", next.history().getLast().getText());
        }
    }

    @Test void usersAppsAndConversationsAreIsolatedAndMemoryIsBounded() {
        try (var context = open()) {
            var store = context.getBean(ConversationStore.class);
            String id = store.create(1L, Conversation.AppType.LOVE).getId();
            String other = store.create(1L, Conversation.AppType.LOVE).getId();
            store.create(1L, Conversation.AppType.MANUS);
            store.create(2L, Conversation.AppType.LOVE);
            assertEquals(2, store.list(1L, Conversation.AppType.LOVE).size());
            assertThrows(NotFoundException.class, () -> store.detail(2L, id));
            assertThrows(NotFoundException.class, () -> store.begin(2L, id, Conversation.AppType.LOVE, "偷看"));
            assertThrows(NotFoundException.class, () -> store.begin(1L, id, Conversation.AppType.MANUS, "跨应用"));
            for (int i = 0; i < 7; i++) {
                var turn = store.begin(1L, id, Conversation.AppType.LOVE, "问题" + i);
                store.append(turn.id(), "回答" + i);
                store.finish(turn.id(), ChatTurn.Status.COMPLETED);
            }
            assertEquals(14, store.detail(1L, id).messages().size());
            var next = store.begin(1L, id, Conversation.AppType.LOVE, "继续");
            assertEquals(10, next.history().size());
            assertEquals("问题2", next.history().getFirst().getText());
            assertEquals("回答6", next.history().getLast().getText());
            assertTrue(store.begin(1L, other, Conversation.AppType.LOVE, "全新的话题").history().isEmpty());
        }
    }

    @Test void concurrentSendsAdmitOnlyOneTurn() throws Exception {
        try (var context = open(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var store = context.getBean(ConversationStore.class);
            String id = store.create(1L, Conversation.AppType.LOVE).getId();
            var start = new CountDownLatch(1);
            Callable<Boolean> send = () -> {
                start.await();
                try { store.begin(1L, id, Conversation.AppType.LOVE, "同时发送"); return true; }
                catch (ConflictException expected) { return false; }
            };
            var a = executor.submit(send);
            var b = executor.submit(send);
            start.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(2, store.detail(1L, id).messages().size());
        }
    }

    @Test void manusReceivesRestoredContextAndSavesItsActualAnswer() {
        try (var context = open()) {
            var store = context.getBean(ConversationStore.class);
            var model = mock(ChatModel.class);
            when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(
                    new org.springframework.ai.chat.model.ChatResponse(List.of(
                            new org.springframework.ai.chat.model.Generation(new org.springframework.ai.chat.messages.AssistantMessage("你叫小林")))));
            var chat = new ConversationChatService(store, mock(LoveApp.class), new ToolCallback[0], model, new HotQuestionCacheService(), mockKnowledge());
            String id = store.create(1L, Conversation.AppType.MANUS).getId();
            var first = store.begin(1L, id, Conversation.AppType.MANUS, "我叫小林");
            store.append(first.id(), "你好小林");
            store.finish(first.id(), ChatTurn.Status.COMPLETED);
            chat.stream(1L, id, Conversation.AppType.MANUS, "我叫什么？").blockLast();
            var prompt = org.mockito.ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
            verify(model, times(1)).call(prompt.capture());
            var texts = prompt.getValue().getInstructions().stream().map(m -> m.getText()).toList();
            assertTrue(texts.containsAll(List.of("我叫小林", "你好小林", "我叫什么？")));
            assertTrue(store.detail(1L, id).messages().getLast().content().contains("你叫小林"));
            assertEquals("COMPLETED", store.detail(1L, id).messages().getLast().status());
        }
    }

    @Test void streamingPersistsBeforeDeliveryHandlesFailureAndCancellation() {
        try (var context = open()) {
            var store = context.getBean(ConversationStore.class);
            var love = mock(LoveApp.class);
            var chat = new ConversationChatService(store, love, new ToolCallback[0], mock(ChatModel.class), new HotQuestionCacheService(), mockKnowledge());
            String id = store.create(1L, Conversation.AppType.LOVE).getId();
            when(love.chatWithHistory(eq("你好"), anyList())).thenReturn(Flux.just("你", "好"));
            var events = chat.stream(1L, id, Conversation.AppType.LOVE, "你好")
                    .doOnNext(event -> {
                        if ("delta".equals(event.event())) assertTrue(store.detail(1L, id).messages().getLast().content().endsWith(event.data()));
                    }).collectList().block();
            assertEquals(List.of("ack", "delta", "delta", "done"), events.stream().map(e -> e.event()).toList());
            assertEquals("COMPLETED", store.detail(1L, id).messages().getLast().status());
            when(love.chatWithHistory(eq("记得吗"), anyList())).thenAnswer(invocation -> {
                List<org.springframework.ai.chat.messages.Message> history = invocation.getArgument(1);
                assertEquals(List.of("你好", "你好"), history.stream().map(m -> m.getText()).toList());
                return Flux.concat(Flux.just("记得"), Flux.error(new RuntimeException("provider unavailable")));
            });
            var failed = chat.stream(1L, id, Conversation.AppType.LOVE, "记得吗").collectList().block();
            assertEquals("error", failed.getLast().event());
            assertEquals("FAILED", store.detail(1L, id).messages().getLast().status());
            assertEquals("记得", store.detail(1L, id).messages().getLast().content());
            when(love.chatWithHistory(eq("取消"), anyList())).thenReturn(Flux.concat(Flux.just("部分回复"), Flux.never()));
            chat.stream(1L, id, Conversation.AppType.LOVE, "取消").take(2).blockLast();
            assertEquals("INTERRUPTED", store.detail(1L, id).messages().getLast().status());
            assertEquals("部分回复", store.detail(1L, id).messages().getLast().content());
        }
    }
}
