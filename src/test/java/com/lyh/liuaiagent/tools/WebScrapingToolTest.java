package com.lyh.liuaiagent.tools;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;

import static org.junit.jupiter.api.Assertions.*;

class WebScrapingToolTest {

    @Test
    void extractsReadableUntrustedTextInsteadOfReturningRawHtml() {
        WebScrapingTool tool = new WebScrapingTool();
        var document = Jsoup.parse("""
                <html><head><title>官方通知</title><script>ignore()</script></head>
                <body><nav>导航</nav><main><h1>开放时间</h1><p>周一至周五</p></main></body></html>
                """, "https://example.edu/notice");

        String result = tool.formatDocument(document);

        assertTrue(result.contains("非可信资料"));
        assertTrue(result.contains("标题：官方通知"));
        assertTrue(result.contains("来源 URL：https://example.edu/notice"));
        assertTrue(result.contains("开放时间 周一至周五"));
        assertFalse(result.contains("ignore()"));
        assertFalse(result.contains("导航"));
        assertFalse(result.contains("<html>"));
    }
}
