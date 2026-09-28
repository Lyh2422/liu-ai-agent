package com.lyh.liuaiagent.knowledge;

import com.lyh.liuaiagent.rag.LoveAppKeywordExpansionService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PersistentKnowledgeIndexTest {

    @Test
    void reusesPersistedVectorsWhenTheKnowledgeVersionDidNotChange() {
        var store = new FakePersistentStore();
        var documents = chunks("guide", "沟通指南", "先表达感受，再讨论解决方案");

        var first = new KnowledgeIndex(store, new LoveAppKeywordExpansionService(), documents);
        int firstWriteCount = store.added;
        var restarted = new KnowledgeIndex(store, new LoveAppKeywordExpansionService(), documents);

        assertEquals(first.cacheVersion(), restarted.cacheVersion());
        assertEquals(documents.size(), firstWriteCount);
        assertEquals(firstWriteCount, store.added, "重启后不应重新计算和写入已有向量");
    }

    @Test
    void searchesOnlyThePublishedVersionAndRemovesDiscardedVectors() {
        var store = new FakePersistentStore();
        var index = new KnowledgeIndex(store, new LoveAppKeywordExpansionService(),
                chunks("guide", "沟通指南", "原始内容"));
        String activeVersion = index.cacheVersion();
        var prepared = index.prepare(chunks("guide", "沟通指南", "修改后的内容"));

        assertEquals(activeVersion, index.cacheVersion(), "prepare 阶段不能提前切换线上索引");
        index.similaritySearch(SearchRequest.builder().query("沟通").topK(3).build());
        assertNotNull(store.lastRequest.getFilterExpression());

        String discardedVersion = prepared.version();
        index.discard(prepared);
        assertFalse(store.versions().contains(discardedVersion));
        assertTrue(store.versions().contains(activeVersion));
    }

    private static List<Document> chunks(String id, String title, String content) {
        var source = new KnowledgeDocument();
        source.setId(id);
        source.setFilename(id + ".md");
        source.setTitle(title);
        source.setContent(content);
        return KnowledgeDocuments.chunks(List.of(source));
    }

    private static final class FakePersistentStore implements PersistentKnowledgeVectorStore {
        private final Map<String, Document> documents = new LinkedHashMap<>();
        private int added;
        private SearchRequest lastRequest;

        @Override
        public Set<String> existingIds(Collection<String> ids) {
            Set<String> existing = new HashSet<>(documents.keySet());
            existing.retainAll(ids);
            return existing;
        }

        @Override
        public void deleteVersion(String version) {
            documents.values().removeIf(document -> version.equals(
                    document.getMetadata().get(KnowledgeIndex.KNOWLEDGE_VERSION_METADATA)));
        }

        @Override
        public void deleteVersionsExcept(String activeVersion) {
            documents.values().removeIf(document -> !activeVersion.equals(
                    document.getMetadata().get(KnowledgeIndex.KNOWLEDGE_VERSION_METADATA)));
        }

        Set<String> versions() {
            Set<String> versions = new HashSet<>();
            documents.values().forEach(document -> versions.add(Objects.toString(
                    document.getMetadata().get(KnowledgeIndex.KNOWLEDGE_VERSION_METADATA))));
            return versions;
        }

        @Override
        public void add(List<Document> documents) {
            documents.forEach(document -> this.documents.put(document.getId(), document));
            added += documents.size();
        }

        @Override public void delete(List<String> ids) { ids.forEach(documents::remove); }
        @Override public void delete(Filter.Expression expression) { throw new UnsupportedOperationException(); }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            lastRequest = request;
            return documents.values().stream().limit(request.getTopK()).toList();
        }
    }
}
