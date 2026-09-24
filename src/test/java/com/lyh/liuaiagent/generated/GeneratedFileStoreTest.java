package com.lyh.liuaiagent.generated;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedFileStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void savesUtf8MarkdownWithSafeDownloadNameAndOpaqueId() throws Exception {
        GeneratedFileStore store = new GeneratedFileStore(tempDir);

        GeneratedFileReference reference = store.saveMarkdown("../周计划", "# 周计划\n\n- 周一复习\n");
        GeneratedFileStore.StoredGeneratedFile stored = store.findMarkdown(reference.id()).orElseThrow();

        assertEquals("周计划.md", reference.filename());
        assertEquals("周计划.md", stored.filename());
        assertEquals("# 周计划\n\n- 周一复习\n", Files.readString(stored.path(), StandardCharsets.UTF_8));
        assertTrue(reference.marker().startsWith("[[generated-file:"));
        assertFalse(store.findMarkdown("../../etc/passwd").isPresent());
    }

    @Test
    void rejectsEmptyAndOversizedMarkdown() {
        GeneratedFileStore store = new GeneratedFileStore(tempDir);

        assertThrows(IllegalArgumentException.class, () -> store.saveMarkdown("empty.md", "  "));
        assertThrows(IllegalArgumentException.class, () -> store.saveMarkdown(
                "large.md", "x".repeat(GeneratedFileStore.MAX_MARKDOWN_CHARS + 1)));
    }
}
