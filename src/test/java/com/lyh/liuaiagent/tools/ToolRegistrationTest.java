package com.lyh.liuaiagent.tools;

import com.lyh.liuaiagent.generated.GeneratedFileStore;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistrationTest {
    @Test
    void registersMarkdownGenerationTool() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("search-api.api-key", "test-key")));
            context.register(GeneratedFileStore.class, ToolRegistration.class);
            context.refresh();

            assertTrue(java.util.Arrays.stream(context.getBean("allTools", org.springframework.ai.tool.ToolCallback[].class))
                    .anyMatch(tool -> "generateMarkdownFile".equals(tool.getToolDefinition().name())));
        }
    }

    @Test
    void excludesDangerousToolsWhenDisabled() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of(
                            "search-api.api-key", "test-key",
                            "agent.tools.dangerous-enabled", "false")));
            context.register(GeneratedFileStore.class, ToolRegistration.class);
            context.refresh();

            var tools = java.util.Arrays.stream(
                    context.getBean("allTools", org.springframework.ai.tool.ToolCallback[].class))
                    .map(tool -> tool.getToolDefinition().name())
                    .toList();
            assertTrue(tools.contains("generateMarkdownFile"));
            assertTrue(tools.contains("searchWeb"));
            assertFalse(tools.contains("executeTerminalCommand"));
            assertFalse(tools.contains("readFile"));
            assertFalse(tools.contains("downloadResource"));
            assertFalse(tools.contains("scrapeWebPage"));
            assertFalse(tools.contains("generatePDF"));
        }
    }
}
