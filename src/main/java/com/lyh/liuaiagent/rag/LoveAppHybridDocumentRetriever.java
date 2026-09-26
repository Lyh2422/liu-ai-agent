package com.lyh.liuaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final int RRF_CONSTANT = 60;
    private static final int CONTEXTUAL_QUERY_MAX_CHARS = 18;

    private final VectorStore vectorStore;
    private final LoveAppRetrievalCorpus corpus;
    private final LoveAppKeywordExpansionService keywordExpansionService;
    private final LoveAppRagProperties properties;

    @Autowired
    public LoveAppHybridDocumentRetriever(VectorStore vectorStore,
                                          LoveAppRetrievalCorpus corpus,
                                          LoveAppKeywordExpansionService keywordExpansionService,
                                          LoveAppRagProperties properties) {
        this.vectorStore = vectorStore;
        this.corpus = corpus;
        this.keywordExpansionService = keywordExpansionService;
        this.properties = properties;
        validate(properties);
    }

    public LoveAppHybridDocumentRetriever(VectorStore vectorStore,
                                          LoveAppRetrievalCorpus corpus,
                                          LoveAppKeywordExpansionService keywordExpansionService) {
        this(vectorStore, corpus, keywordExpansionService, new LoveAppRagProperties());
    }

    @Override
    public List<Document> retrieve(Query query) {
        if (vectorStore instanceof com.lyh.liuaiagent.knowledge.KnowledgeIndex managed) {
            var snapshot = managed.snapshot();
            if (snapshot.corpus().documents().isEmpty()) return List.of();
            return new LoveAppHybridDocumentRetriever(snapshot.vectors(), snapshot.corpus(), keywordExpansionService, properties).retrieve(query);
        }
        String userQuery = contextualize(query);
        List<String> variants = keywordExpansionService.expand(userQuery);

        Map<String, RankedDocument> mergedDocuments = new LinkedHashMap<>();
        boolean vectorAvailable = true;

        for (String variant : variants) {
            if (vectorAvailable) {
                try {
                    accumulateVector(mergedDocuments, vectorSearch(variant));
                } catch (RestClientException error) {
                    // 查询 embedding 依赖外部 HTTP 服务；失败时本地 BM25 仍可检索。
                    // 本次请求不再为其他扩展版本重复调用故障接口，下次请求会重新尝试。
                    vectorAvailable = false;
                    log.warn("向量检索服务不可用，本次请求继续使用 BM25；异常类型={}", error.getClass().getSimpleName());
                }
            }
            accumulateBm25(mergedDocuments, bm25Search(variant));
        }

        return mergedDocuments.values().stream()
                .sorted(Comparator.comparingDouble(RankedDocument::score).reversed())
                .limit(properties.getFinalTopK())
                .map(this::toDocument)
                .toList();
    }

    /**
     * 仅在“他呢”“那我怎么办”这类短指代问题中补入上一条用户消息，避免把完整问题
     * 无条件与历史拼接而引入噪声。补全只用于检索，最终回答仍使用原始问题。
     */
    private String contextualize(Query query) {
        String current = keywordExpansionService.normalize(query.text());
        if (current.length() > CONTEXTUAL_QUERY_MAX_CHARS || !looksContextDependent(current)) return current;
        for (int index = query.history().size() - 1; index >= 0; index--) {
            var message = query.history().get(index);
            if (message.getMessageType() != MessageType.USER) continue;
            String previous = keywordExpansionService.normalize(message.getText());
            if (!previous.isBlank()) return previous + " " + current;
        }
        return current;
    }

    private static boolean looksContextDependent(String query) {
        return query.matches("(?:他|她|对方)?呢")
                || query.matches("(?:那|然后|接下来|之后).*?")
                || query.matches(".*(?:这件事|那个|之前的).*?")
                || query.matches("(?:我|我们)?该?怎么办[呢吗]?");
    }

    private List<Document> vectorSearch(String query) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(VECTOR_TOP_K)
                .similarityThreshold(properties.getVectorSimilarityThreshold())
                .build());
    }

    private List<LoveAppRetrievalCorpus.ScoredDocument> bm25Search(String query) {
        return corpus.bm25Search(query, BM25_TOP_K);
    }

    private void accumulateVector(Map<String, RankedDocument> mergedDocuments, List<Document> documents) {
        Set<String> seen = new LinkedHashSet<>();
        int rank = 0;
        for (Document document : documents) {
            String key = resolveDocumentKey(document);
            // 同一张候选榜内按 ID 去重，防止重复片段为自己多次投票。
            if (!seen.add(key)) continue;
            RankedDocument rankedDocument = mergedDocuments.computeIfAbsent(key, ignored -> new RankedDocument(document));
            rankedDocument.addSource("vector", ++rank, document.getScore());
        }
    }

    private void accumulateBm25(Map<String, RankedDocument> mergedDocuments,
                                List<LoveAppRetrievalCorpus.ScoredDocument> documents) {
        Set<String> seen = new LinkedHashSet<>();
        int rank = 0;
        for (LoveAppRetrievalCorpus.ScoredDocument scored : documents) {
            Document document = scored.document();
            String key = resolveDocumentKey(document);
            if (!seen.add(key)) continue;
            RankedDocument rankedDocument = mergedDocuments.computeIfAbsent(key, ignored -> new RankedDocument(document));
            rankedDocument.addSource("bm25", ++rank, scored.score());
        }
    }

    private Document toDocument(RankedDocument rankedDocument) {
        var builder = Document.builder()
                .id(rankedDocument.document().getId())
                .text(rankedDocument.document().getText())
                .metadata(new LinkedHashMap<>(rankedDocument.document().getMetadata()))
                .metadata("hybrid_score", rankedDocument.score())
                .metadata("hybrid_sources", String.join(",", rankedDocument.sources()))
                .score(rankedDocument.score());
        if (rankedDocument.vectorScore() != null) {
            builder.metadata("vector_score", rankedDocument.vectorScore());
        }
        if (rankedDocument.bm25Score() != null) {
            builder.metadata("bm25_score", rankedDocument.bm25Score());
        }
        return builder.build();
    }

    private String resolveDocumentKey(Document document) {
        if (document.getId() != null && !document.getId().isBlank()) {
            return document.getId();
        }
        Object filename = document.getMetadata().get("filename");
        return (filename == null ? "document" : filename.toString()) + "#" + document.getText().hashCode();
    }

    private static void validate(LoveAppRagProperties properties) {
        double threshold = properties.getVectorSimilarityThreshold();
        if (threshold < 0.0d || threshold > 1.0d) {
            throw new IllegalArgumentException("love-app.rag.vector-similarity-threshold 必须在 0 到 1 之间");
        }
        if (properties.getFinalTopK() < 1 || properties.getFinalTopK() > 10) {
            throw new IllegalArgumentException("love-app.rag.final-top-k 必须在 1 到 10 之间");
        }
    }

    private static final class RankedDocument {
        private final Document document;
        private double score;
        private final Set<String> sources = new LinkedHashSet<>();
        private Double vectorScore;
        private Double bm25Score;

        private RankedDocument(Document document) {
            this.document = document;
        }

        private void addSource(String source, int rank, Double rawScore) {
            sources.add(source);
            score += 1.0d / (RRF_CONSTANT + rank);
            if ("vector".equals(source) && rawScore != null) {
                vectorScore = vectorScore == null ? rawScore : Math.max(vectorScore, rawScore);
            }
            if ("bm25".equals(source) && rawScore != null) {
                bm25Score = bm25Score == null ? rawScore : Math.max(bm25Score, rawScore);
            }
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

        private Double vectorScore() {
            return vectorScore;
        }

        private Double bm25Score() {
            return bm25Score;
        }
    }
}
