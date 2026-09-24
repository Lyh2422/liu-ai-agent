package com.lyh.liuaiagent.tools;

import com.lyh.liuaiagent.generated.GeneratedFileStore;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Value("${agent.tools.dangerous-enabled:true}")
    private boolean dangerousToolsEnabled;

    @Bean
    public ToolCallback[] allTools(GeneratedFileStore generatedFiles) {
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        MarkdownGenerationTool markdownGenerationTool = new MarkdownGenerationTool(generatedFiles);
        TerminateTool terminateTool = new TerminateTool();

        if (!dangerousToolsEnabled) {
            return ToolCallbacks.from(
                webSearchTool,
                markdownGenerationTool,
                terminateTool
            );
        }

        FileOperationTool fileOperationTool = new FileOperationTool();
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        return ToolCallbacks.from(
            fileOperationTool,
            webSearchTool,
            webScrapingTool,
            resourceDownloadTool,
            terminalOperationTool,
            pdfGenerationTool,
            markdownGenerationTool,
            terminateTool
        );
    }
}
