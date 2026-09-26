package com.lyh.liuaiagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search Baidu for candidate source pages. Results are discovery snippets only; open a relevant URL with the web scraping tool before treating a claim as verified.")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        if (apiKey == null || apiKey.isBlank()) {
            return unavailable("缺少 SearchAPI 密钥");
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            return formatSearchResults(response);
        } catch (Exception e) {
            return unavailable("请求失败");
        }
    }

    String formatSearchResults(String response) {
        JSONObject jsonObject = JSONUtil.parseObj(response);
        JSONArray organicResults = jsonObject.getJSONArray("organic_results");
        if (organicResults == null || organicResults.isEmpty()) {
            String message = jsonObject.getStr("error");
            if (message == null || message.isBlank()) {
                message = jsonObject.getStr("message");
            }
            String detail = message == null || message.isBlank() ? "搜索接口没有返回可用结果" : message;
            return unavailable(detail);
        }
        // 搜索结果只用于发现来源；最终事实应打开原页面核实。
        List<Object> objects = organicResults.subList(0, Math.min(5, organicResults.size()));
        return "以下内容是搜索引擎返回的非可信候选来源，只用于发现页面，不能把摘要直接当作已核实事实：\n"
                + objects.stream().map(obj -> formatCandidate((JSONObject) obj))
                .collect(Collectors.joining("\n\n"));
    }

    private static String formatCandidate(JSONObject result) {
        return "标题：" + safe(result.getStr("title")) + "\n"
                + "URL：" + safe(result.getStr("link")) + "\n"
                + "摘要：" + safe(result.getStr("snippet")) + "\n"
                + "下一步：如需使用该信息，请打开 URL 核对原文。";
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "未提供" : value.replaceAll("[\\r\\n]+", " ").strip();
    }

    private static String unavailable(String detail) {
        return "搜索服务暂时不可用：" + detail
                + "。请不要继续重试搜索。若用户问题依赖实时或精确事实，必须说明目前无法核实，不得凭已有知识补写。";
    }
}
