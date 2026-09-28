package com.lyh.liuaiagent.rag;

import com.lyh.liuaiagent.knowledge.KnowledgeIndex;
import com.lyh.liuaiagent.knowledge.PgVectorKnowledgeStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(LoveAppPgVectorProperties.class)
public class LoveAppVectorStoreConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "love-app.rag.pgvector.enabled", havingValue = "true")
    public PgVectorKnowledgeStore pgVectorKnowledgeStore(LoveAppPgVectorProperties properties,
                                                          EmbeddingModel dashscopeEmbeddingModel) {
        return new PgVectorKnowledgeStore(properties, dashscopeEmbeddingModel);
    }

    @Bean
    @Primary
    public KnowledgeIndex loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel,
                                             LoveAppRetrievalCorpus corpus,
                                             LoveAppKeywordExpansionService expansion,
                                             ObjectProvider<PgVectorKnowledgeStore> pgVector) {
        PgVectorKnowledgeStore persistent = pgVector.getIfAvailable();
        return persistent == null
                ? new KnowledgeIndex(dashscopeEmbeddingModel, expansion, corpus.documents())
                : new KnowledgeIndex(persistent, expansion, corpus.documents());
    }
}
