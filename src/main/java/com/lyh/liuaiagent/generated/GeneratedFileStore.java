package com.lyh.liuaiagent.generated;

import com.lyh.liuaiagent.constant.FileConstant;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;

@Service
public class GeneratedFileStore {
    public static final int MAX_MARKDOWN_CHARS = 500_000;
    private static final int MAX_FILENAME_CHARS = 100;

    private final Path root;

    public GeneratedFileStore() {
        this(Path.of(FileConstant.FILE_SAVE_DIR, "generated", "markdown"));
    }

    public GeneratedFileStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public GeneratedFileReference saveMarkdown(String requestedFilename, String content) throws IOException {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Markdown 内容不能为空");
        if (content.length() > MAX_MARKDOWN_CHARS) {
            throw new IllegalArgumentException("Markdown 内容不能超过 " + MAX_MARKDOWN_CHARS + " 个字符");
        }
        String filename = normalizeMarkdownFilename(requestedFilename);
        String id = UUID.randomUUID().toString();
        Files.createDirectories(root);
        Path contentPath = contentPath(id);
        Path namePath = namePath(id);
        try {
            Files.writeString(contentPath, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.writeString(namePath, filename, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException error) {
            Files.deleteIfExists(contentPath);
            Files.deleteIfExists(namePath);
            throw error;
        }
        return new GeneratedFileReference(id, filename);
    }

    public Optional<StoredGeneratedFile> findMarkdown(String id) {
        if (!isUuid(id)) return Optional.empty();
        Path contentPath = contentPath(id);
        Path namePath = namePath(id);
        if (!Files.isRegularFile(contentPath) || !Files.isRegularFile(namePath)) return Optional.empty();
        try {
            String filename = normalizeMarkdownFilename(Files.readString(namePath, StandardCharsets.UTF_8));
            return Optional.of(new StoredGeneratedFile(contentPath, filename, Files.size(contentPath)));
        } catch (IOException | IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    static String normalizeMarkdownFilename(String requestedFilename) {
        String filename = requestedFilename == null ? "" : requestedFilename.strip();
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        filename = filename.replaceAll("[^\\p{L}\\p{N}._ -]", "_")
                .replaceAll("\\s+", " ")
                .replaceAll("^\\.+", "")
                .strip();
        if (filename.toLowerCase().endsWith(".md")) {
            filename = filename.substring(0, filename.length() - 3).strip();
        }
        if (filename.isBlank()) filename = "AI整理文档";
        int maxBaseLength = MAX_FILENAME_CHARS - 3;
        if (filename.length() > maxBaseLength) filename = filename.substring(0, maxBaseLength).strip();
        return filename + ".md";
    }

    private boolean isUuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private Path contentPath(String id) {
        return root.resolve(id + ".md");
    }

    private Path namePath(String id) {
        return root.resolve(id + ".name");
    }

    public record StoredGeneratedFile(Path path, String filename, long size) {}
}
