package com.lyh.liuaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LoveAppRetrievalCorpus {

    private final LoveAppDocumentLoader documentLoader;
    private final LoveAppKeywordExpansionService keywordExpansionService;

    private List<Document> documents = List.of();
    private Map<String, DocumentStats> documentStatsById = Map.of();
    private double averageDocumentLength;

    public LoveAppRetrievalCorpus(LoveAppDocumentLoader documentLoader,
                                  LoveAppKeywordExpansionService keywordExpansionService) {
        this.documentLoader = documentLoader;
        this.keywordExpansionService = keywordExpansionService;
    }

    @PostConstruct
    void initialize() {
        replaceDocuments(documentLoader.loadMarkdowns());
    }

    public void replaceDocuments(List<Document> source) {
        List<Document> loadedDocuments = List.copyOf(source);
        this.documents = loadedDocuments;

        Map<String, DocumentStats> statsById = new LinkedHashMap<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        long totalLength = 0L;

        for (int index = 0; index < loadedDocuments.size(); index++) {
            Document document = loadedDocuments.get(index);
            String documentKey = resolveDocumentKey(document, index);
            List<String> tokens = keywordExpansionService.tokenize(buildSearchableText(document));
            Map<String, Integer> termFrequency = buildTermFrequency(tokens);
            Set<String> uniqueTokens = new LinkedHashSet<>(tokens);

            uniqueTokens.forEach(token -> documentFrequency.merge(token, 1, Integer::sum));

            DocumentStats stats = new DocumentStats(document, documentKey, tokens.size(), termFrequency, uniqueTokens);
            statsById.put(documentKey, stats);
            totalLength += tokens.size();
        }

        this.documentStatsById = statsById;
        this.averageDocumentLength = loadedDocuments.isEmpty() ? 0.0 : totalLength * 1.0 / loadedDocuments.size();

        Map<String, Double> idfByToken = new HashMap<>();
        int totalDocumentCount = loadedDocuments.size();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            int documentCount = entry.getValue();
            double idf = Math.log(1.0 + (totalDocumentCount - documentCount + 0.5) / (documentCount + 0.5));
            idfByToken.put(entry.getKey(), idf);
        }

        this.documentStatsById = statsById.values().stream()
                .map(stats -> stats.withIdf(idfByToken))
                .collect(Collectors.toMap(DocumentStats::documentKey, value -> value, (left, right) -> left, LinkedHashMap::new));
    }

    public List<Document> documents() {
        return documents;
    }

    public List<ScoredDocument> bm25Search(String query, int topK) {
        if (topK <= 0) return List.of();
        List<String> queryTokens = keywordExpansionService.extractKeywords(query);
        if (queryTokens.isEmpty()) return List.of();

        List<ScoredDocument> scoredDocuments = new ArrayList<>();
        for (DocumentStats stats : documentStatsById.values()) {
            double score = bm25Score(queryTokens, stats);
            if (score > 0.0d) {
                scoredDocuments.add(new ScoredDocument(stats.document(), score));
            }
        }

        scoredDocuments.sort(Comparator.comparingDouble(ScoredDocument::score).reversed());
        return scoredDocuments.stream().limit(topK).toList();
    }

    public String buildSearchableText(Document document) {
        Object filename = document.getMetadata().get("filename");
        String metadataText = filename == null ? "" : " " + filename;
        return document.getText() + metadataText;
    }

    private double bm25Score(List<String> queryTokens, DocumentStats stats) {
        if (queryTokens.isEmpty() || stats.documentLength() == 0) {
            return 0.0d;
        }

        final double k1 = 1.5d;
        final double b = 0.75d;
        double score = 0.0d;

        Set<String> uniqueQueryTokens = new LinkedHashSet<>(queryTokens);
        for (String token : uniqueQueryTokens) {
            Integer tf = stats.termFrequency().get(token);
            Double idf = stats.idfByToken().get(token);
            if (tf == null || tf == 0 || idf == null) {
                continue;
            }

            double denominator = tf + k1 * (1.0d - b + b * stats.documentLength() / Math.max(averageDocumentLength, 1.0d));
            score += idf * tf * (k1 + 1.0d) / denominator;
        }

        return score;
    }

    private Map<String, Integer> buildTermFrequency(List<String> tokens) {
        Map<String, Integer> frequency = new HashMap<>();
        for (String token : tokens) {
            frequency.merge(token, 1, Integer::sum);
        }
        return frequency;
    }

    private String resolveDocumentKey(Document document, int index) {
        if (document.getId() != null && !document.getId().isBlank()) {
            return document.getId();
        }
        Object filename = document.getMetadata().get("filename");
        return (filename == null ? "document" : filename.toString()) + "#" + index;
    }

    record ScoredDocument(Document document, double score) {
    }

    private record DocumentStats(
            Document document,
            String documentKey,
            int documentLength,
            Map<String, Integer> termFrequency,
            Set<String> uniqueTokens,
            Map<String, Double> idfByToken
    ) {
        DocumentStats(Document document, String documentKey, int documentLength, Map<String, Integer> termFrequency, Set<String> uniqueTokens) {
            this(document, documentKey, documentLength, termFrequency, uniqueTokens, Map.of());
        }

        DocumentStats withIdf(Map<String, Double> idfByToken) {
            return new DocumentStats(document, documentKey, documentLength, termFrequency, uniqueTokens, idfByToken);
        }
    }
}
