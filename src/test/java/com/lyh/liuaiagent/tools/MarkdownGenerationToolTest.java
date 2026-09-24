package com.lyh.liuaiagent.tools;

import com.lyh.liuaiagent.generated.GeneratedFileReference;
import com.lyh.liuaiagent.generated.GeneratedFileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownGenerationToolTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesDownloadableMarkdownReference() {
        GeneratedFileStore store = new GeneratedFileStore(tempDir);
        MarkdownGenerationTool tool = new MarkdownGenerationTool(store);

        String result = tool.generateMarkdownFile("学习计划", "# 学习计划\n\n1. 完成复习");
        List<GeneratedFileReference> references = GeneratedFileReference.findAll(result);

        assertTrue(result.contains("Markdown 文件已生成"));
        assertEquals(1, references.size());
        assertEquals("学习计划.md", references.getFirst().filename());
        assertTrue(store.findMarkdown(references.getFirst().id()).isPresent());
    }
}
