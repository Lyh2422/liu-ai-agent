package com.lyh.liuaiagent.knowledge;

import org.springframework.ai.document.Document;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 稳定切片 ID 使未变化的切片可以复用已有向量。保留原文，避免编辑后内容被 Markdown 解析器丢弃。 */
public final class KnowledgeDocuments {
    private KnowledgeDocuments() {}
    public static List<Document> chunks(List<KnowledgeDocument> documents) {
        List<Document> chunks = new ArrayList<>();
        for (var document : documents) {
            String text = document.getContent();
            for (int start = 0; start < text.length();) {
                int end = Math.min(start + 1000, text.length());
                if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
                String chunk = text.substring(start, end);
                if (!chunk.isBlank()) {
                    String id = UUID.nameUUIDFromBytes((document.getId() + ":" + start + ":" + document.getTitle() + ":" + chunk)
                            .getBytes(StandardCharsets.UTF_8)).toString();
                    chunks.add(Document.builder().id(id).text(document.getTitle() + "\n" + chunk)
                            .metadata("documentId", document.getId()).metadata("filename", document.getFilename())
                            .metadata("title", document.getTitle()).build());
                }
                if (end == text.length()) break;
                start = end - 100;
                if (Character.isLowSurrogate(text.charAt(start))) start--;
            }
        }
        return chunks;
    }
}
