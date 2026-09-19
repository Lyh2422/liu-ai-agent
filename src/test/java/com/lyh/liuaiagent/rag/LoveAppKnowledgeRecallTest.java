package com.lyh.liuaiagent.rag;

import com.lyh.liuaiagent.knowledge.KnowledgeDocument;
import com.lyh.liuaiagent.knowledge.KnowledgeDocuments;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用实际 Markdown 和生产切片逻辑；只测词法补召回，不冒充真实向量模型评测。 */
class LoveAppKnowledgeRecallTest {
    @ParameterizedTest
    @CsvSource({
            "她天天黏着我一点自己的时间都没有,日常相处篇,个人空间",
            "他老让我买单,日常相处篇,消费",
            "她总翻我手机,亲密边界与尊重篇,手机密码",
            "我们不在一个城市,异地与未来规划篇,异地",
            "我只想说说他却只会讲道理,情绪支持与自我成长篇,倾诉",
            "我忘不掉前任,分手与重新开始篇,前任"
    })
    void retrievesExpectedKnowledgeWithVectorsDisabled(String question, String expectedFile, String expectedContent) throws Exception {
        var expansion = new LoveAppKeywordExpansionService();
        var corpus = new LoveAppRetrievalCorpus(null, expansion);
        List<KnowledgeDocument> sources = new ArrayList<>();
        for (var resource : new PathMatchingResourcePatternResolver().getResources("classpath:document/*.md")) {
            var source = new KnowledgeDocument();
            source.setId(resource.getFilename());
            source.setFilename(resource.getFilename());
            source.setTitle(resource.getFilename().replaceFirst("\\.md$", ""));
            source.setContent(resource.getContentAsString(StandardCharsets.UTF_8));
            sources.add(source);
        }
        assertFalse(sources.isEmpty());
        corpus.replaceDocuments(KnowledgeDocuments.chunks(sources));
        var vectors = mock(VectorStore.class);
        when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        List<Document> results = new LoveAppHybridDocumentRetriever(vectors, corpus, expansion).retrieve(new Query(question));
        assertTrue(results.stream().limit(3).anyMatch(document ->
                        document.getMetadata().get("filename").toString().contains(expectedFile)
                                && document.getText().contains(expectedContent)),
                () -> "前三个片段未命中：" + question + " -> " + results.stream().map(Document::getMetadata).toList());
    }
}
