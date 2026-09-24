package com.lyh.liuaiagent.tools;

import com.lyh.liuaiagent.generated.GeneratedFileReference;
import com.lyh.liuaiagent.generated.GeneratedFileStore;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class MarkdownGenerationTool {
    private final GeneratedFileStore generatedFiles;

    public MarkdownGenerationTool(GeneratedFileStore generatedFiles) {
        this.generatedFiles = generatedFiles;
    }

    @Tool(description = "将整理完成的内容生成为 UTF-8 Markdown 文件，供用户下载。用户要求生成、导出或下载 Markdown/MD 文档时必须使用此工具。")
    public String generateMarkdownFile(
            @ToolParam(description = "下载文件名，例如 学习计划.md；如果未带 .md 会自动补充") String fileName,
            @ToolParam(description = "完整的 Markdown 正文，应包含清晰的标题、段落和必要的列表") String content) {
        try {
            GeneratedFileReference reference = generatedFiles.saveMarkdown(fileName, content);
            return "Markdown 文件已生成，文件名：" + reference.filename() + "\n" + reference.marker();
        } catch (IllegalArgumentException error) {
            return "Markdown 文件生成失败：" + error.getMessage();
        } catch (Exception error) {
            return "Markdown 文件生成失败，请稍后重试";
        }
    }
}
