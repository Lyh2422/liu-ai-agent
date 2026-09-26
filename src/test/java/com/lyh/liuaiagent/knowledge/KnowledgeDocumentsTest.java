package com.lyh.liuaiagent.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeDocumentsTest {

    @Test
    void prefersParagraphBoundariesAndKeepsTraceableOffsets() {
        var source = new KnowledgeDocument();
        source.setId("guide");
        source.setFilename("guide.md");
        source.setTitle("沟通指南");
        source.setContent("第一段。" + "甲".repeat(650) + "\n\n第二段。" + "乙".repeat(650));

        var chunks = KnowledgeDocuments.chunks(List.of(source));

        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.getFirst().getText().endsWith("\n\n"));
        assertEquals(0, chunks.getFirst().getMetadata().get("chunkStart"));
        assertTrue((int) chunks.getFirst().getMetadata().get("chunkEnd") <= 1000);
        assertEquals("guide", chunks.getFirst().getMetadata().get("documentId"));
    }
}
