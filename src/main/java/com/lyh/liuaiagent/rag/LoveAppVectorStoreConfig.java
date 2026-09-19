package com.lyh.liuaiagent.rag;

import com.lyh.liuaiagent.knowledge.KnowledgeIndex;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoveAppVectorStoreConfig {
    @Bean
    public KnowledgeIndex loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel,
                                             LoveAppRetrievalCorpus corpus,
                                             LoveAppKeywordExpansionService expansion) {
        return new KnowledgeIndex(dashscopeEmbeddingModel, expansion, corpus.documents());
    }
}
