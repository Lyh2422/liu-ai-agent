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

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        if (apiKey == null || apiKey.isBlank()) {
            return "搜索服务暂时不可用：缺少 SearchAPI 密钥。请不要继续重试搜索，可基于已有知识完成用户任务。";
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            return formatSearchResults(response);
        } catch (Exception e) {
            return "搜索服务暂时不可用：" + e.getMessage() + "。请不要继续重试搜索，可基于已有知识完成用户任务。";
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
            return "搜索服务暂时不可用：" + detail + "。请不要继续重试搜索，可基于已有知识完成用户任务。";
        }
        // 取出返回结果的前 5 条；搜索接口有时少于 5 条，不能直接 subList(0, 5)。
        List<Object> objects = organicResults.subList(0, Math.min(5, organicResults.size()));
        // 拼接搜索结果为字符串
        return objects.stream().map(obj -> {
            JSONObject tmpJSONObject = (JSONObject) obj;
            return tmpJSONObject.toString();
        }).collect(Collectors.joining(","));
    }
}
