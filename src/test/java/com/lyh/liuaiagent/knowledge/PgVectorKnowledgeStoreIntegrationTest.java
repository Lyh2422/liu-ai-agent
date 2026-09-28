package com.lyh.liuaiagent.knowledge;

import com.lyh.liuaiagent.rag.LoveAppPgVectorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.ai.vectorstore.SearchRequest;

import java.sql.DriverManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PgVectorKnowledgeStoreIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "PGVECTOR_TEST_URL", matches = ".+")
    void persistsAndSearchesVectorsAcrossConnections() throws Exception {
        String url = System.getenv("PGVECTOR_TEST_URL");
        String username = System.getenv().getOrDefault("PGVECTOR_TEST_USERNAME", "liu_ai_agent");
        String password = System.getenv().getOrDefault("PGVECTOR_TEST_PASSWORD", "liu_ai_agent_vector_dev");
        String table = "love_vectors_it_" + UUID.randomUUID().toString().replace("-", "");
        String id = UUID.randomUUID().toString();
        var properties = properties(url, username, password, table);

        Exception failure = null;
        try {
            try (var store = new PgVectorKnowledgeStore(properties, new FixedEmbeddingModel())) {
                store.add(List.of(Document.builder().id(id).text("个人空间与沟通边界")
                        .metadata(KnowledgeIndex.KNOWLEDGE_VERSION_METADATA, "v1")
                        .metadata("chunkId", "chunk-1").build()));
                assertTrue(store.existingIds(Set.of(id)).contains(id));
                var results = store.similaritySearch(SearchRequest.builder().query("个人空间")
                        .topK(3).similarityThresholdAll().build());
                assertEquals(id, results.getFirst().getId());
            }

            try (var reopened = new PgVectorKnowledgeStore(properties, new FixedEmbeddingModel())) {
                assertTrue(reopened.existingIds(Set.of(id)).contains(id), "关闭连接池后向量仍应保留");
                reopened.deleteVersion("v1");
                assertTrue(reopened.existingIds(Set.of(id)).isEmpty());
            }
        } catch (Exception error) {
            failure = error;
            throw error;
        } finally {
            try (var connection = DriverManager.getConnection(url, username, password);
                 var statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS public." + table);
            } catch (Exception cleanupError) {
                if (failure != null) failure.addSuppressed(cleanupError);
                else throw cleanupError;
            }
        }
    }

    private static LoveAppPgVectorProperties properties(String url, String username, String password, String table) {
        var properties = new LoveAppPgVectorProperties();
        properties.setEnabled(true);
        properties.setUrl(url);
        properties.setUsername(username);
        properties.setPassword(password);
        properties.setTable(table);
        properties.setDimensions(3);
        properties.setMaximumPoolSize(2);
        return properties;
    }

    private static final class FixedEmbeddingModel implements EmbeddingModel {
        @Override public float[] embed(Document document) { return vector(document.getText()); }
        @Override public int dimensions() { return 3; }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            var embeddings = java.util.stream.IntStream.range(0, request.getInstructions().size())
                    .mapToObj(index -> new Embedding(vector(request.getInstructions().get(index)), index)).toList();
            return new EmbeddingResponse(embeddings);
        }

        private static float[] vector(String text) {
            return text.contains("空间") ? new float[]{1, 0, 0} : new float[]{0, 1, 0};
        }
    }
}
