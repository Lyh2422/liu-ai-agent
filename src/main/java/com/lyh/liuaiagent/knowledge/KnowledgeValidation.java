package com.lyh.liuaiagent.knowledge;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Locale;

public final class KnowledgeValidation {
    public static final int MAX_BYTES = 512 * 1024;
    public static final int MAX_CHARS = 200_000;
    private KnowledgeValidation() {}

    public static String filename(String name) {
        if (name == null || name.isBlank() || name.length() > 255 || name.contains("/") || name.contains("\\")
                || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("文件名不合法");
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".md") && !lower.endsWith(".txt")) throw new IllegalArgumentException("仅支持 Markdown（.md）和纯文本（.txt）文件");
        return name;
    }

    public static String read(MultipartFile file) throws IOException {
        filename(file.getOriginalFilename());
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw new IllegalArgumentException("文件不能为空，且不能超过 512 KB");
        byte[] bytes;
        try (var input = file.getInputStream()) { bytes = input.readNBytes(MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("文件不能超过 512 KB");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return content(text.startsWith("\uFEFF") ? text.substring(1) : text);
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("请上传 UTF-8 编码的文本文件");
        }
    }

    public static String content(String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("文档内容不能为空");
        if (content.length() > MAX_CHARS || content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("文档不能超过 200000 字符或 512 KB");
        }
        if (content.codePoints().anyMatch(value -> Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t')) {
            throw new IllegalArgumentException("文件包含非文本内容，请上传 Markdown 或纯文本文件");
        }
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    public static String title(String title) {
        if (title == null || title.isBlank() || title.strip().length() > 120 || title.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("标题不能为空，且不能超过 120 字符");
        }
        return title.strip();
    }
}
