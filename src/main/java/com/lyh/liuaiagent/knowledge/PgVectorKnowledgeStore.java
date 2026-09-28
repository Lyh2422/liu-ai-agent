package com.lyh.liuaiagent.knowledge;

import com.lyh.liuaiagent.rag.LoveAppPgVectorProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 使用独立连接池访问 PGVector，避免影响承载业务数据的 MySQL DataSource。 */
public final class PgVectorKnowledgeStore implements PersistentKnowledgeVectorStore, AutoCloseable {
    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbc;
    private final PgVectorStore delegate;
    private final String qualifiedTable;

    public PgVectorKnowledgeStore(LoveAppPgVectorProperties properties, EmbeddingModel embeddingModel) {
        validate(properties);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName("org.postgresql.Driver");
        config.setPoolName("love-pgvector");
        config.setMaximumPoolSize(properties.getMaximumPoolSize());
        config.setMinimumIdle(0);
        this.dataSource = new HikariDataSource(config);
        this.jdbc = new JdbcTemplate(dataSource);
        this.qualifiedTable = properties.getSchema() + "." + properties.getTable();

        var builder = PgVectorStore.builder(jdbc, embeddingModel)
                .schemaName(properties.getSchema())
                .vectorTableName(properties.getTable())
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true);
        if (properties.getDimensions() > 0) builder.dimensions(properties.getDimensions());
        this.delegate = builder.build();
        this.delegate.afterPropertiesSet();
    }

    @Override
    public void add(List<Document> documents) {
        delegate.add(documents);
    }

    @Override
    public void delete(List<String> ids) {
        delegate.delete(ids);
    }

    @Override
    public void delete(Filter.Expression expression) {
        delegate.delete(expression);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        return delegate.similaritySearch(request);
    }

    @Override
    public Set<String> existingIds(Collection<String> ids) {
        if (ids.isEmpty()) return Set.of();
        Set<String> wanted = new HashSet<>(ids);
        Set<String> existing = new HashSet<>();
        jdbc.query("SELECT id::text FROM " + qualifiedTable, result -> {
            String id = result.getString(1);
            if (wanted.contains(id)) existing.add(id);
        });
        return Set.copyOf(existing);
    }

    @Override
    public void deleteVersion(String version) {
        jdbc.update("DELETE FROM " + qualifiedTable + " WHERE metadata->>'knowledgeVersion' = ?", version);
    }

    @Override
    public void deleteVersionsExcept(String activeVersion) {
        jdbc.update("DELETE FROM " + qualifiedTable
                + " WHERE metadata->>'knowledgeVersion' IS NULL OR metadata->>'knowledgeVersion' <> ?", activeVersion);
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private static void validate(LoveAppPgVectorProperties properties) {
        if (properties.getUrl() == null || !properties.getUrl().startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("PGVector URL 必须使用 jdbc:postgresql://");
        }
        if (!identifier(properties.getSchema()) || !identifier(properties.getTable())) {
            throw new IllegalArgumentException("PGVector schema/table 只能包含字母、数字和下划线，且不能以数字开头");
        }
        if (properties.getDimensions() < 0) throw new IllegalArgumentException("PGVector dimensions 不能为负数");
        if (properties.getMaximumPoolSize() < 1) throw new IllegalArgumentException("PGVector 连接池容量必须为正数");
    }

    private static boolean identifier(String value) {
        return value != null && value.matches("[A-Za-z_][A-Za-z0-9_]*");
    }
}
