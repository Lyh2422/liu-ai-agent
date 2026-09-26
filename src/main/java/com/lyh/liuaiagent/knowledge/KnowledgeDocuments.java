package com.lyh.liuaiagent.knowledge;

import org.springframework.ai.document.Document;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 稳定切片 ID 使未变化的切片可以复用已有向量。保留原文，避免编辑后内容被 Markdown 解析器丢弃。 */
public final class KnowledgeDocuments {
    private static final int MAX_CHUNK_CHARS = 1000;
    private static final int MIN_CHUNK_CHARS = 600;
    private static final int CHUNK_OVERLAP_CHARS = 100;

    private KnowledgeDocuments() {}
    public static List<Document> chunks(List<KnowledgeDocument> documents) {
        List<Document> chunks = new ArrayList<>();
        for (var document : documents) {
            String text = document.getContent();
            for (int start = 0; start < text.length();) {
                int end = chooseBoundary(text, start);
                if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
                String chunk = text.substring(start, end);
                if (!chunk.isBlank()) {
                    String id = UUID.nameUUIDFromBytes((document.getId() + ":" + start + ":" + document.getTitle() + ":" + chunk)
                            .getBytes(StandardCharsets.UTF_8)).toString();
                    chunks.add(Document.builder().id(id).text(document.getTitle() + "\n" + chunk)
                            .metadata("documentId", document.getId()).metadata("filename", document.getFilename())
                            .metadata("title", document.getTitle()).metadata("chunkStart", start)
                            .metadata("chunkEnd", end).build());
                }
                if (end == text.length()) break;
                start = Math.max(start + 1, end - CHUNK_OVERLAP_CHARS);
                if (Character.isLowSurrogate(text.charAt(start))) start--;
            }
        }
        return chunks;
    }

    /** 尽量在段落或完整句子结束处分片，避免固定长度把论点和条件切开。 */
    private static int chooseBoundary(String text, int start) {
        int hardEnd = Math.min(start + MAX_CHUNK_CHARS, text.length());
        if (hardEnd == text.length()) return hardEnd;
        int minEnd = Math.min(start + MIN_CHUNK_CHARS, hardEnd);
        int paragraph = text.lastIndexOf("\n\n", hardEnd);
        if (paragraph >= minEnd) return paragraph + 2;
        for (int index = hardEnd - 1; index >= minEnd; index--) {
            char value = text.charAt(index);
            if (value == '。' || value == '！' || value == '？' || value == '\n') return index + 1;
        }
        return hardEnd;
    }
}
