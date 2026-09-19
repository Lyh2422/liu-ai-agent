package com.lyh.liuaiagent.knowledge;

import com.lyh.liuaiagent.auth.exception.ConflictException;
import com.lyh.liuaiagent.auth.exception.NotFoundException;
import com.lyh.liuaiagent.rag.*;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.context.annotation.*;
import org.springframework.core.io.*;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeManagementTest {
    @TempDir Path temp;
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = KnowledgeDocumentRepository.class)
    @Import({KnowledgeDocumentStore.class, KnowledgeManagementService.class})
    static class Config {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource); factory.setPackagesToScan("com.lyh.liuaiagent.knowledge");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "update")); return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean ResourcePatternResolver resources() throws Exception {
            var resolver = mock(ResourcePatternResolver.class);
            var seed = new ByteArrayResource("校园恋爱沟通知识".getBytes(StandardCharsets.UTF_8)) {
                @Override public String getFilename() { return "校园沟通.md"; }
            };
            when(resolver.getResources("classpath:document/*.md")).thenReturn(new Resource[]{seed});
            return resolver;
        }
        @Bean EmbeddingModel model() {
            var model = mock(EmbeddingModel.class);
            when(model.embed(any(Document.class))).thenReturn(new float[]{1, 0, 1});
            when(model.embed(anyString())).thenReturn(new float[]{1, 0, 1});
            return model;
        }
        @Bean KnowledgeIndex index(EmbeddingModel model, KnowledgeDocumentStore store) {
            return new KnowledgeIndex(model, new LoveAppKeywordExpansionService(), KnowledgeDocuments.chunks(store.all()));
        }
    }
    AnnotationConfigApplicationContext open() {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> new DriverManagerDataSource("jdbc:h2:file:" + temp.resolve("knowledge") + ";DB_CLOSE_ON_EXIT=FALSE", "sa", ""));
        context.register(Config.class); context.refresh(); return context;
    }
    static MockMultipartFile file(String name, String content) { return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8)); }

    @Test void uploadEditDeleteUpdateBothIndexesAndReuseUnchangedEmbeddings() throws Exception {
        try (var context = open()) {
            var management = context.getBean(KnowledgeManagementService.class);
            var store = context.getBean(KnowledgeDocumentStore.class);
            var index = context.getBean(KnowledgeIndex.class);
            var model = context.getBean(EmbeddingModel.class);
            clearInvocations(model);
            var created = management.upload(file("星河.md", "星河活动在周五举行"), 1L);
            assertEquals(2, store.all().size());
            verify(model, times(1)).embed(any(Document.class));
            assertFalse(index.snapshot().corpus().bm25Search("星河", 5).isEmpty());
            var oldSnapshot = index.snapshot();
            var updated = management.update(created.getId(), created.getVersion(), "星河新安排", "星河活动改为周六举行", 2L);
            assertEquals(created.getVersion() + 1, updated.getVersion());
            var retriever = new LoveAppHybridDocumentRetriever(index, oldSnapshot.corpus(), new LoveAppKeywordExpansionService());
            var results = retriever.retrieve(new Query("星河活动"));
            assertTrue(results.stream().anyMatch(document -> document.getText().contains("周六")));
            assertFalse(results.stream().anyMatch(document -> document.getText().contains("周五")));
            assertTrue(oldSnapshot.vectors().similaritySearch(SearchRequest.builder().query("星河").build()).stream().anyMatch(document -> document.getText().contains("周五")));
            clearInvocations(model);
            management.delete(created.getId(), updated.getVersion());
            verify(model, never()).embed(any(Document.class));
            assertThrows(NotFoundException.class, () -> store.get(created.getId()));
            assertTrue(index.snapshot().corpus().bm25Search("星河", 5).isEmpty());
            assertFalse(index.similaritySearch(SearchRequest.builder().query("星河").build()).stream().anyMatch(document -> created.getId().equals(document.getMetadata().get("documentId"))));
        }
    }

    @Test void restartPreservesEditsAndDoesNotResurrectDeletedBuiltinDocuments() throws Exception {
        String uploadedId;
        try (var context = open()) {
            var management = context.getBean(KnowledgeManagementService.class);
            var store = context.getBean(KnowledgeDocumentStore.class);
            var builtin = store.all().getFirst();
            management.delete(builtin.getId(), builtin.getVersion());
            var uploaded = management.upload(file("约会.txt", "原始约会计划"), 1L);
            uploadedId = uploaded.getId();
            management.update(uploadedId, uploaded.getVersion(), "约会计划", "更新后的约会计划", 1L);
        }
        try (var context = open()) {
            var store = context.getBean(KnowledgeDocumentStore.class);
            assertEquals(1, store.all().size());
            assertEquals("更新后的约会计划", store.get(uploadedId).getContent());
            assertFalse(context.getBean(KnowledgeIndex.class).snapshot().corpus().bm25Search("约会", 5).isEmpty());
        }
    }

    @Test void embeddingFailureAndStaleVersionNeverPublishOrPersistTheChange() throws Exception {
        try (var context = open()) {
            var management = context.getBean(KnowledgeManagementService.class);
            var store = context.getBean(KnowledgeDocumentStore.class);
            var index = context.getBean(KnowledgeIndex.class);
            var original = store.all().getFirst();
            var snapshot = index.snapshot();
            when(context.getBean(EmbeddingModel.class).embed(any(Document.class))).thenThrow(new RuntimeException("embedding unavailable"));
            assertThrows(KnowledgeManagementService.IndexUpdateException.class, () -> management.upload(file("失败.txt", "这次上传失败"), 1L));
            assertThrows(KnowledgeManagementService.IndexUpdateException.class, () -> management.update(original.getId(), original.getVersion(), "新标题", "新内容", 1L));
            assertEquals(1, store.all().size());
            assertEquals(original.getContent(), store.get(original.getId()).getContent());
            assertSame(snapshot, index.snapshot());
            assertThrows(ConflictException.class, () -> management.update(original.getId(), original.getVersion() + 1, "更新", "过期编辑", 1L));
            assertThrows(ConflictException.class, () -> management.delete(original.getId(), original.getVersion() + 1));
        }
    }

    @Test void emptyKnowledgeBaseRemainsSearchableWithoutCallingEmbeddingService() {
        try (var context = open()) {
            var store = context.getBean(KnowledgeDocumentStore.class);
            var document = store.all().getFirst();
            var index = context.getBean(KnowledgeIndex.class);
            context.getBean(KnowledgeManagementService.class).delete(document.getId(), document.getVersion());
            var model = context.getBean(EmbeddingModel.class); clearInvocations(model);
            var retriever = new LoveAppHybridDocumentRetriever(index, index.snapshot().corpus(), new LoveAppKeywordExpansionService());
            assertTrue(retriever.retrieve(new Query("你好")).isEmpty());
            verifyNoInteractions(model);
        }
    }

    @Test void maintainedKnowledgeReachesActualChatPromptAlongsideConversationHistory() throws Exception {
        try (var context = open()) {
            context.getBean(KnowledgeManagementService.class).upload(file("星河活动.md", "星河活动只在周六开展"), 1L);
            var index = context.getBean(KnowledgeIndex.class);
            var retriever = new LoveAppHybridDocumentRetriever(index, index.snapshot().corpus(), new LoveAppKeywordExpansionService());
            var model = mock(org.springframework.ai.chat.model.ChatModel.class);
            when(model.stream(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(reactor.core.publisher.Flux.just(
                    new org.springframework.ai.chat.model.ChatResponse(List.of(new org.springframework.ai.chat.model.Generation(new org.springframework.ai.chat.messages.AssistantMessage("周六"))))));
            var love = new com.lyh.liuaiagent.app.LoveApp(model);
            org.springframework.test.util.ReflectionTestUtils.setField(love, "loveAppRagCloudAdvisor", new LoveAppRagCloudAdvisorConfig(retriever).loveAppRagCloudAdvisor());
            assertEquals("周六", love.chatWithHistory("星河活动哪天举行", List.of(new org.springframework.ai.chat.messages.UserMessage("我叫小林"), new org.springframework.ai.chat.messages.AssistantMessage("你好"))).blockLast());
            var captor = org.mockito.ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
            verify(model).stream(captor.capture());
            var texts = captor.getValue().getInstructions().stream().map(org.springframework.ai.chat.messages.Message::getText).toList();
            assertTrue(texts.contains("我叫小林"));
            assertTrue(texts.stream().anyMatch(text -> text.contains("星河活动只在周六开展")));
        }
    }

    @Test void uploadsRejectUnsupportedUnsafeOrOversizedTextAndAcceptUtf8Bom() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> KnowledgeValidation.read(file("test.pdf", "pdf")));
        assertThrows(IllegalArgumentException.class, () -> KnowledgeValidation.read(file("../test.md", "hello")));
        assertThrows(IllegalArgumentException.class, () -> KnowledgeValidation.read(file("test.md", "  ")));
        assertThrows(IllegalArgumentException.class, () -> KnowledgeValidation.read(file("test.txt", "a\u0000b")));
        assertThrows(IllegalArgumentException.class, () -> KnowledgeValidation.read(new MockMultipartFile("file", "test.txt", "text/plain", new byte[]{(byte) 0xff})));
        assertThrows(IllegalArgumentException.class, () -> KnowledgeValidation.read(new MockMultipartFile("file", "test.txt", "text/plain", new byte[KnowledgeValidation.MAX_BYTES + 1])));
        assertEquals("你好\n知识库", KnowledgeValidation.read(file("test.MD", "\uFEFF你好\r\n知识库")));
    }
}
