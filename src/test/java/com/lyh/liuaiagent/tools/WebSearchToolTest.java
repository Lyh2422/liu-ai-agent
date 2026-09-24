package com.lyh.liuaiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchToolTest {
    @Test
    void missingApiKeyReturnsRecoverableMessage() {
        WebSearchTool webSearchTool = new WebSearchTool("");

        String result = webSearchTool.searchWeb("程序员鱼皮编程导航 codefather.cn");

        assertTrue(result.contains("搜索服务暂时不可用"));
        assertTrue(result.contains("请不要继续重试搜索"));
    }

    @Test
    void formatsEmptyOrganicResultsAsRecoverableMessage() {
        WebSearchTool webSearchTool = new WebSearchTool("test-key");

        String result = webSearchTool.formatSearchResults("{\"search_metadata\":{\"status\":\"Success\"}}");

        assertTrue(result.contains("搜索服务暂时不可用"));
        assertTrue(result.contains("请不要继续重试搜索"));
    }

    @Test
    void formatsFewerThanFiveResultsWithoutSubListError() {
        WebSearchTool webSearchTool = new WebSearchTool("test-key");

        String result = webSearchTool.formatSearchResults("""
                {"organic_results":[{"title":"河南博物院"},{"title":"二七广场"}]}
                """);

        assertTrue(result.contains("河南博物院"));
        assertTrue(result.contains("二七广场"));
    }
}
