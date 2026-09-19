package com.lyh.liuaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@lombok.extern.slf4j.Slf4j
public class LoveAppHybridDocumentRetriever implements DocumentRetriever {

    private static final int VECTOR_TOP_K = 5;
    private static final int BM25_TOP_K = 5;
    private static final int FINAL_TOP_K = 6;
    private static final int RRF_CONSTANT = 60;

    private final VectorStore vectorStore;
    private final LoveAppRetrievalCorpus corpus;
    private final LoveAppKeywordExpansionService keywordExpansionService;

    public LoveAppHybridDocumentRetriever(VectorStore vectorStore,
                                          LoveAppRetrievalCorpus corpus,
                                          LoveAppKeywordExpansionService keywordExpansionService) {
        this.vectorStore = vectorStore;
        this.corpus = corpus;
        this.keywordExpansionService = keywordExpansionService;
    }

    @Override
    public List<Document> retrieve(Query query) {
        if (vectorStore instanceof com.lyh.liuaiagent.knowledge.KnowledgeIndex managed) {
            var snapshot = managed.snapshot();
            if (snapshot.corpus().documents().isEmpty()) return List.of();
            return new LoveAppHybridDocumentRetriever(snapshot.vectors(), snapshot.corpus(), keywordExpansionService).retrieve(query);
        }
        String userQuery = keywordExpansionService.normalize(query.text());
        List<String> variants = keywordExpansionService.expand(userQuery);

        Map<String, RankedDocument> mergedDocuments = new LinkedHashMap<>();
        boolean vectorAvailable = true;

        for (String variant : variants) {
            if (vectorAvailable) {
                try {
                    accumulate(mergedDocuments, vectorSearch(variant), "vector");
                } catch (RestClientException error) {
                    // 查询 embedding 依赖外部 HTTP 服务；失败时本地 BM25 仍可检索。
                    // 本次请求不再为其他扩展版本重复调用故障接口，下次请求会重新尝试。
                    vectorAvailable = false;
                    log.warn("向量检索服务不可用，本次请求继续使用 BM25；异常类型={}", error.getClass().getSimpleName());
                }
            }
            accumulate(mergedDocuments, bm25Search(variant), "bm25");
        }

        return mergedDocuments.values().stream()
                .sorted(Comparator.comparingDouble(RankedDocument::score).reversed())
                .limit(FINAL_TOP_K)
                .map(this::toDocument)
                .toList();
    }

    private List<Document> vectorSearch(String query) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(VECTOR_TOP_K)
                .similarityThresholdAll()
                .build());
    }

    private List<Document> bm25Search(String query) {
        return corpus.bm25Search(query, BM25_TOP_K).stream()
                .map(LoveAppRetrievalCorpus.ScoredDocument::document)
                .toList();
    }

    private void accumulate(Map<String, RankedDocument> mergedDocuments, List<Document> documents, String source) {
        Set<String> seen = new LinkedHashSet<>();
        int rank = 0;
        for (Document document : documents) {
            String key = resolveDocumentKey(document);
            // 同一张候选榜内按 ID 去重，防止重复片段为自己多次投票。
            if (!seen.add(key)) continue;
            RankedDocument rankedDocument = mergedDocuments.computeIfAbsent(key, ignored -> new RankedDocument(document));
            rankedDocument.addSource(source, ++rank);
        }
    }

    private Document toDocument(RankedDocument rankedDocument) {
        return Document.builder()
                .id(rankedDocument.document().getId())
                .text(rankedDocument.document().getText())
                .metadata(new LinkedHashMap<>(rankedDocument.document().getMetadata()))
                .metadata("hybrid_score", rankedDocument.score())
                .metadata("hybrid_sources", String.join(",", rankedDocument.sources()))
                .score(rankedDocument.score())
                .build();
    }

    private String resolveDocumentKey(Document document) {
        if (document.getId() != null && !document.getId().isBlank()) {
            return document.getId();
        }
        Object filename = document.getMetadata().get("filename");
        return (filename == null ? "document" : filename.toString()) + "#" + document.getText().hashCode();
    }

    private static final class RankedDocument {
        private final Document document;
        private double score;
        private final Set<String> sources = new LinkedHashSet<>();

        private RankedDocument(Document document) {
            this.document = document;
        }

        private void addSource(String source, int rank) {
            sources.add(source);
            score += 1.0d / (RRF_CONSTANT + rank);
        }

        private Document document() {
            return document;
        }

        private double score() {
            return score;
        }

        private Set<String> sources() {
            return sources;
        }
    }
}
