package com.lyh.liuaiagent.rag;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 可按实际 embedding 模型和评测集调节的 RAG 参数。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "love-app.rag")
public class LoveAppRagProperties {

    public static final double DEFAULT_VECTOR_SIMILARITY_THRESHOLD = 0.72d;
    public static final int DEFAULT_FINAL_TOP_K = 4;

    /**
     * 向量相似度低于该值的候选不会进入 RRF。不同 embedding 模型的分数分布不同，
     * 上线后应以标注数据集调参，而不是把该值理解成通用的“正确率”。
     */
    private double vectorSimilarityThreshold = DEFAULT_VECTOR_SIMILARITY_THRESHOLD;

    /** 限制最终注入模型的片段数量，减少长上下文中的无关资料。 */
    private int finalTopK = DEFAULT_FINAL_TOP_K;
}
