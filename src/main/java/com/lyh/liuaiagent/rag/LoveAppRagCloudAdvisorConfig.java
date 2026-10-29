package com.lyh.liuaiagent.rag;

import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

/**
 * 自定义混合召回 RAG 增强顾问
 */
@Configuration
@EnableConfigurationProperties(LoveAppRagProperties.class)
public class LoveAppRagCloudAdvisorConfig {

    private final LoveAppHybridDocumentRetriever loveAppHybridDocumentRetriever;

    public LoveAppRagCloudAdvisorConfig(LoveAppHybridDocumentRetriever loveAppHybridDocumentRetriever) {
        this.loveAppHybridDocumentRetriever = loveAppHybridDocumentRetriever;
    }

    @Value("${app.conversation.memory.rag-context-token-budget:2500}")
    private int ragContextTokenBudget = 2500;

    @Bean
    public Advisor loveAppRagCloudAdvisor() {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(loveAppHybridDocumentRetriever)
                .queryAugmenter(new LoveAppContextualQueryAugmenter(ragContextTokenBudget))
                .build();
    }

}
