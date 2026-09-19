package com.lyh.liuaiagent.knowledge;

import com.lyh.liuaiagent.rag.LoveAppKeywordExpansionService;
import com.lyh.liuaiagent.rag.LoveAppRetrievalCorpus;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.*;
import org.springframework.ai.vectorstore.filter.Filter;
import java.util.*;
import java.util.stream.Collectors;

/** 向量与关键词索引作为同一个快照发布。正在执行的检索使用其开始时的完整快照。 */
public class KnowledgeIndex implements VectorStore {
    public record Snapshot(VectorStore vectors, LoveAppRetrievalCorpus corpus, String version) {}
    private final EmbeddingModel embeddingModel;
    private final LoveAppKeywordExpansionService expansion;
    private volatile Snapshot snapshot;

    public KnowledgeIndex(EmbeddingModel embeddingModel, LoveAppKeywordExpansionService expansion, List<Document> documents) {
        this.embeddingModel = embeddingModel;
        this.expansion = expansion;
        this.snapshot = prepare(documents);
    }

    public Snapshot snapshot() { return snapshot; }
    public String cacheVersion() { return snapshot.version(); }

    public Snapshot prepare(List<Document> documents) {
        var candidate = new CopyableVectorStore(embeddingModel);
        if (snapshot != null) candidate.copyFrom((CopyableVectorStore) snapshot.vectors());
        Set<String> ids = documents.stream().map(Document::getId).collect(Collectors.toSet());
        candidate.retain(ids);
        List<Document> changed = documents.stream().filter(document -> !candidate.contains(document.getId())).toList();
        if (!changed.isEmpty()) candidate.add(changed);
        var corpus = new LoveAppRetrievalCorpus(null, expansion);
        corpus.replaceDocuments(documents);
        return new Snapshot(candidate, corpus, UUID.randomUUID().toString());
    }

    public void publish(Snapshot prepared) { this.snapshot = Objects.requireNonNull(prepared); }
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
}
