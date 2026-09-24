package com.lyh.liuaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoveAppHybridDocumentRetrieverTest {

    @Test
    void rejectsWeakVectorCandidatesBeforeFusionAndKeepsRawScoresForDiagnostics() {
        var expansion = new LoveAppKeywordExpansionService();
        var corpus = mock(LoveAppRetrievalCorpus.class);
        var vectors = mock(VectorStore.class);
        var vectorDocument = Document.builder().id("vector").text("相关片段").score(0.88d).build();
        when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(vectorDocument));
        when(corpus.bm25Search("星河", 5)).thenReturn(List.of(
                new LoveAppRetrievalCorpus.ScoredDocument(vectorDocument, 1.25d)));

        var results = new LoveAppHybridDocumentRetriever(vectors, corpus, expansion).retrieve(new Query("星河"));

        var request = org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectors).similaritySearch(request.capture());
        assertEquals(LoveAppRagProperties.DEFAULT_VECTOR_SIMILARITY_THRESHOLD,
                request.getValue().getSimilarityThreshold(), 1e-12);
        assertEquals(0.88d, (double) results.getFirst().getMetadata().get("vector_score"), 1e-12);
        assertEquals(1.25d, (double) results.getFirst().getMetadata().get("bm25_score"), 1e-12);
    }

    @Test
    void allowsAValidatedDeploymentSpecificVectorThreshold() {
        var properties = new LoveAppRagProperties();
        properties.setVectorSimilarityThreshold(0.81d);
        var vectors = mock(VectorStore.class);
        when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        var corpus = mock(LoveAppRetrievalCorpus.class);

        new LoveAppHybridDocumentRetriever(vectors, corpus, new LoveAppKeywordExpansionService(), properties)
                .retrieve(new Query("星河"));

        var request = org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectors).similaritySearch(request.capture());
        assertEquals(0.81d, request.getValue().getSimilarityThreshold(), 1e-12);
        properties.setVectorSimilarityThreshold(1.01d);
        assertThrows(IllegalArgumentException.class,
                () -> new LoveAppHybridDocumentRetriever(vectors, corpus, new LoveAppKeywordExpansionService(), properties));
    }

    @Test
    void fallsBackToAllBm25VariantsOnConnectionResetAndRetriesVectorsOnNextRequest() {
        var expansion = new LoveAppKeywordExpansionService();
        var corpus = new LoveAppRetrievalCorpusForTest(List.of(doc("space", "个人空间和联系频率")), expansion);
        var vectors = mock(VectorStore.class);
        when(vectors.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("connection reset", new java.net.SocketException("Connection reset")))
                .thenReturn(List.of(doc("space", "个人空间和联系频率")));
        var retriever = new LoveAppHybridDocumentRetriever(vectors, corpus, expansion);
        var first = retriever.retrieve(new Query("天天黏着我"));
        assertEquals("space", first.getFirst().getId());
        assertEquals("bm25", first.getFirst().getMetadata().get("hybrid_sources"));
        verify(vectors, times(1)).similaritySearch(any(SearchRequest.class));
        var recovered = retriever.retrieve(new Query("天天黏着我"));
        assertEquals("vector,bm25", recovered.getFirst().getMetadata().get("hybrid_sources"));
        verify(vectors, times(3)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void returnsEmptyContextInsteadOfFailingWhenRemoteResponseBreaksAndBm25HasNoMatch() {
        var expansion = new LoveAppKeywordExpansionService();
        var corpus = new LoveAppRetrievalCorpusForTest(List.of(doc("space", "个人空间")), expansion);
        var vectors = mock(VectorStore.class);
        when(vectors.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("response extraction failed", new java.net.SocketException("Unexpected end of file from server")));
        assertTrue(new LoveAppHybridDocumentRetriever(vectors, corpus, expansion).retrieve(new Query("你好")).isEmpty());
    }

    @Test
    void doesNotHideLocalProgrammingErrorsAsRemoteServiceFailures() {
        var expansion = new LoveAppKeywordExpansionService();
        var corpus = new LoveAppRetrievalCorpusForTest(List.of(doc("space", "个人空间")), expansion);
        var vectors = mock(VectorStore.class);
        when(vectors.similaritySearch(any(SearchRequest.class))).thenThrow(new IllegalArgumentException("invalid dimensions"));
        assertThrows(IllegalArgumentException.class,
                () -> new LoveAppHybridDocumentRetriever(vectors, corpus, expansion).retrieve(new Query("个人空间")));
    }

    @Test
    void keywordExpansionRescuesAColloquialQueryWhenVectorsMiss() {
        var expansion = new LoveAppKeywordExpansionService();
        var corpus = new LoveAppRetrievalCorpusForTest(List.of(
                doc("space", "个人空间、边界感和联系频率"), doc("other", "毕业城市规划")), expansion);
        String query = "她天天黏着我，一点自己的时间都没有怎么办？";
        assertTrue(corpus.bm25Search(query, 5).isEmpty(), "原句与正式术语没有词项交集");
        var vectors = mock(VectorStore.class);
        when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        var results = new LoveAppHybridDocumentRetriever(vectors, corpus, expansion).retrieve(new Query(query));
        assertEquals(List.of("space"), results.stream().map(Document::getId).toList());
        assertEquals("bm25", results.getFirst().getMetadata().get("hybrid_sources"));
        assertEquals(1.0 / 61, (double) results.getFirst().getMetadata().get("hybrid_score"), 1e-12);
        verify(vectors, times(2)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void fusesRanksInsteadOfIncomparableRawScoresAndDeduplicatesEachList() {
        var expansion = new LoveAppKeywordExpansionService();
        var corpus = mock(LoveAppRetrievalCorpus.class);
        var vectorOnly = doc("vector", "向量单路");
        var shared = doc("shared", "两路命中");
        var keywordOnly = doc("keyword", "关键词单路");
        var vectors = mock(VectorStore.class);
        when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(vectorOnly, vectorOnly, shared));
        when(corpus.bm25Search("星河", 5)).thenReturn(List.of(
                new LoveAppRetrievalCorpus.ScoredDocument(shared, 0.01),
                new LoveAppRetrievalCorpus.ScoredDocument(keywordOnly, 0.001)));
        var results = new LoveAppHybridDocumentRetriever(vectors, corpus, expansion).retrieve(new Query("星河"));
        assertEquals(List.of("shared", "vector", "keyword"), results.stream().map(Document::getId).toList());
        assertEquals(1.0 / 62 + 1.0 / 61, (double) results.getFirst().getMetadata().get("hybrid_score"), 1e-12);
        assertEquals(1.0 / 61, (double) results.get(1).getMetadata().get("hybrid_score"), 1e-12);
        assertEquals("vector,bm25", results.getFirst().getMetadata().get("hybrid_sources"));
        assertFalse(shared.getMetadata().containsKey("hybrid_score"), "不能修改索引内原文档的元数据");
    }

    @Test
    void limitsFinalContextAndSkipsEmptyQueries() {
        var expansion = new LoveAppKeywordExpansionService();
        var corpus = mock(LoveAppRetrievalCorpus.class);
        var vectors = mock(VectorStore.class);
        when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(
                java.util.stream.IntStream.range(0, 5).mapToObj(i -> doc("v" + i, "向量")).toList());
        when(corpus.bm25Search("星河", 5)).thenReturn(
                java.util.stream.IntStream.range(0, 5).mapToObj(i -> new LoveAppRetrievalCorpus.ScoredDocument(doc("b" + i, "关键词"), 1)).toList());
        var retriever = new LoveAppHybridDocumentRetriever(vectors, corpus, expansion);
        assertEquals(6, retriever.retrieve(new Query("星河")).size());
        clearInvocations(vectors, corpus);
        assertTrue(retriever.retrieve(new Query("？！")).isEmpty());
        verifyNoInteractions(vectors, corpus);
    }

    @Test
    void shouldBoostKeywordAndSemanticHitsTogether() {
        LoveAppKeywordExpansionService expansionService = new LoveAppKeywordExpansionService();
        LoveAppRetrievalCorpus corpus = new LoveAppRetrievalCorpusForTest(List.of(
                doc("d1", "对方太粘人、每天都要见面时，先谈个人空间和联系频率，再给替代方案。"),
                doc("d2", "异地恋要靠报备、共同体验和见面计划维持稳定。"),
                doc("d3", "吵架后不要冷战，先找台阶再道歉。")
        ), expansionService);

        VectorStore vectorStore = new InMemoryVectorStore(List.of(
                doc("d1", "对方太粘人、每天都要见面时，先谈个人空间和联系频率，再给替代方案。"),
                doc("d2", "异地恋要靠报备、共同体验和见面计划维持稳定。"),
                doc("d3", "吵架后不要冷战，先找台阶再道歉。")
        ));

        LoveAppHybridDocumentRetriever retriever = new LoveAppHybridDocumentRetriever(vectorStore, corpus, expansionService);
        List<Document> documents = retriever.retrieve(new Query("对方太粘人，我有点喘不过气怎么办？"));

        assertTrue(documents.stream().anyMatch(document -> document.getText().contains("个人空间")));
        assertTrue(documents.stream().anyMatch(document -> document.getText().contains("联系频率")));
    }

    private static Document doc(String id, String text) {
        return Document.builder().id(id).text(text).metadata(Map.of("filename", id + ".md")).build();
    }

    private static final class LoveAppRetrievalCorpusForTest extends LoveAppRetrievalCorpus {

        LoveAppRetrievalCorpusForTest(List<Document> documents, LoveAppKeywordExpansionService keywordExpansionService) {
            super(new NoopLoader(documents), keywordExpansionService);
            initialize();
        }
    }

    private static final class NoopLoader extends LoveAppDocumentLoader {
        private final List<Document> documents;

        NoopLoader(List<Document> documents) {
            super(null);
            this.documents = documents;
        }

        @Override
        public List<Document> loadMarkdowns() {
            return documents;
        }
    }

    private static final class InMemoryVectorStore implements VectorStore {
        private final List<Document> documents;

        InMemoryVectorStore(List<Document> documents) {
            this.documents = documents;
        }

        @Override
        public void add(List<Document> documents) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(List<String> list) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(org.springframework.ai.vectorstore.filter.Filter.Expression expression) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            List<Document> result = new ArrayList<>();
            if (request.getQuery().contains("粘人") || request.getQuery().contains("个人空间")) {
                result.add(documents.get(0));
            }
            if (request.getQuery().contains("异地")) {
                result.add(documents.get(1));
            }
            if (request.getQuery().contains("冷战")) {
                result.add(documents.get(2));
            }
            return result;
        }
    }
}
