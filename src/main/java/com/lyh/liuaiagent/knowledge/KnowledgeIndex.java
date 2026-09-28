package com.lyh.liuaiagent.knowledge;

import com.lyh.liuaiagent.rag.LoveAppKeywordExpansionService;
import com.lyh.liuaiagent.rag.LoveAppRetrievalCorpus;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/** 向量与关键词索引作为同一个快照发布。正在执行的检索使用其开始时的完整快照。 */
@lombok.extern.slf4j.Slf4j
public class KnowledgeIndex implements VectorStore {
    static final String KNOWLEDGE_VERSION_METADATA = "knowledgeVersion";

    public record Snapshot(VectorStore vectors, LoveAppRetrievalCorpus corpus, String version) {}

    private final EmbeddingModel embeddingModel;
    private final PersistentKnowledgeVectorStore persistentStore;
    private final LoveAppKeywordExpansionService expansion;
    private volatile Snapshot snapshot;
    private String retainedVersion;

    /** 测试和未启用 PGVector 的部署仍使用原有内存索引。 */
    public KnowledgeIndex(EmbeddingModel embeddingModel, LoveAppKeywordExpansionService expansion,
                          List<Document> documents) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel);
        this.persistentStore = null;
        this.expansion = Objects.requireNonNull(expansion);
        this.snapshot = prepareInMemory(documents);
    }

    /** PGVector 模式使用稳定版本号和元数据过滤，重启时可复用已持久化的向量。 */
    public KnowledgeIndex(PersistentKnowledgeVectorStore persistentStore,
                          LoveAppKeywordExpansionService expansion, List<Document> documents) {
        this.embeddingModel = null;
        this.persistentStore = Objects.requireNonNull(persistentStore);
        this.expansion = Objects.requireNonNull(expansion);
        Snapshot initial = preparePersistent(documents);
        this.snapshot = initial;
        persistentStore.deleteVersionsExcept(initial.version());
    }

    public Snapshot snapshot() { return snapshot; }
    public String cacheVersion() { return snapshot.version(); }

    public synchronized Snapshot prepare(List<Document> documents) {
        return persistentStore == null ? prepareInMemory(documents) : preparePersistent(documents);
    }

    public synchronized void publish(Snapshot prepared) {
        Objects.requireNonNull(prepared);
        Snapshot previous = snapshot;
        snapshot = prepared;
        if (persistentStore == null || previous == null || previous.version().equals(prepared.version())) return;
        if (retainedVersion != null && !retainedVersion.equals(previous.version())) {
            safeDeleteVersion(retainedVersion);
        }
        // 保留上一代向量，给已经取得旧快照的并发请求留出完成时间。
        retainedVersion = previous.version();
    }

    /** 数据库写入失败时移除尚未发布的 PGVector 版本，避免孤儿向量累积。 */
    public synchronized void discard(Snapshot prepared) {
        if (persistentStore != null && prepared != null && !prepared.version().equals(snapshot.version())) {
            safeDeleteVersion(prepared.version());
        }
    }

    private Snapshot prepareInMemory(List<Document> documents) {
        var candidate = new CopyableVectorStore(embeddingModel);
        if (snapshot != null) candidate.copyFrom((CopyableVectorStore) snapshot.vectors());
        Set<String> ids = documents.stream().map(Document::getId).collect(Collectors.toSet());
        candidate.retain(ids);
        List<Document> changed = documents.stream().filter(document -> !candidate.contains(document.getId())).toList();
        if (!changed.isEmpty()) candidate.add(changed);
        return snapshot(candidate, documents, UUID.randomUUID().toString());
    }

    private Snapshot preparePersistent(List<Document> documents) {
        String version = contentVersion(documents);
        List<Document> versioned = documents.stream().map(document -> versioned(document, version)).toList();
        Set<String> existing = persistentStore.existingIds(versioned.stream().map(Document::getId).toList());
        List<Document> missing = versioned.stream().filter(document -> !existing.contains(document.getId())).toList();
        try {
            if (!missing.isEmpty()) persistentStore.add(missing);
        } catch (RuntimeException error) {
            if (snapshot == null || !version.equals(snapshot.version())) safeDeleteVersion(version);
            throw error;
        }
        VectorStore view = new VersionFilteredVectorStore(persistentStore, version, documents.isEmpty());
        return snapshot(view, documents, version);
    }

    private void safeDeleteVersion(String version) {
        try {
            persistentStore.deleteVersion(version);
        } catch (RuntimeException cleanupError) {
            log.warn("PGVector 旧知识版本 {} 清理失败，不影响当前已发布索引；异常类型={}",
                    version, cleanupError.getClass().getSimpleName());
        }
    }

    private Snapshot snapshot(VectorStore vectors, List<Document> documents, String version) {
        var corpus = new LoveAppRetrievalCorpus(null, expansion);
        corpus.replaceDocuments(documents);
        return new Snapshot(vectors, corpus, version);
    }

    private static Document versioned(Document document, String version) {
        String chunkId = Objects.toString(document.getMetadata().get("chunkId"), document.getId());
        String vectorId = UUID.nameUUIDFromBytes(("knowledge-vector:" + version + ":" + chunkId)
                .getBytes(StandardCharsets.UTF_8)).toString();
        Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
        metadata.put("chunkId", chunkId);
        metadata.put(KNOWLEDGE_VERSION_METADATA, version);
        return Document.builder().id(vectorId).text(document.getText()).metadata(metadata).build();
    }

    /** 切片 ID 已包含标题与正文，因此排序后哈希可以稳定代表整个知识版本。 */
    private static String contentVersion(List<Document> documents) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            documents.stream().map(Document::getId).sorted().forEach(id -> {
                byte[] bytes = id.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 not available", error);
        }
    }

    @Override public List<Document> similaritySearch(SearchRequest request) { return snapshot.vectors().similaritySearch(request); }
    @Override public void add(List<Document> documents) { throw new UnsupportedOperationException("请通过知识库管理服务修改文档"); }
    @Override public void delete(List<String> ids) { throw new UnsupportedOperationException("请通过知识库管理服务删除文档"); }
    @Override public void delete(Filter.Expression expression) { throw new UnsupportedOperationException("请通过知识库管理服务删除文档"); }

    private static final class CopyableVectorStore extends SimpleVectorStore {
        CopyableVectorStore(EmbeddingModel model) { super(SimpleVectorStore.builder(model)); }
        void copyFrom(CopyableVectorStore source) { store.putAll(source.store); }
        boolean contains(String id) { return store.containsKey(id); }
        void retain(Set<String> ids) { store.keySet().retainAll(ids); }
    }

    private record VersionFilteredVectorStore(VectorStore delegate, String version, boolean empty) implements VectorStore {
        @Override public void add(List<Document> documents) { throw new UnsupportedOperationException(); }
        @Override public void delete(List<String> ids) { throw new UnsupportedOperationException(); }
        @Override public void delete(Filter.Expression expression) { throw new UnsupportedOperationException(); }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            if (empty) return List.of();
            Filter.Expression versionFilter = new FilterExpressionBuilder()
                    .eq(KNOWLEDGE_VERSION_METADATA, version).build();
            Filter.Expression filter = request.getFilterExpression() == null ? versionFilter
                    : new Filter.Expression(Filter.ExpressionType.AND, request.getFilterExpression(), versionFilter);
            return delegate.similaritySearch(SearchRequest.from(request).filterExpression(filter).build());
        }
    }
}
