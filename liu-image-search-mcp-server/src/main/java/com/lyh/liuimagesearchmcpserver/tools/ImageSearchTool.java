package com.lyh.liuimagesearchmcpserver.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ImageSearchTool {
    // 日志对象，用于打印调试信息
    private static final Logger log = LoggerFactory.getLogger(ImageSearchTool.class);

    // 替换为你的 Pexels API 密钥（需从官网申请）
    private static final String API_KEY = "twcZq4fpr7OGGBHVUMOPYBSCeT32moXvP9XjMyzDzszyz81IabQAVwGs";

    // Pexels 常规搜索接口（请以文档为准）
    private static final String API_URL = "https://api.pexels.com/v1/search";

    @Tool(description = "search image from web")
    public String searchImage(@ToolParam(description = "Search query keyword") String query) {
        try {
            List<String> imageUrls = searchMediumImages(query);
            // 打印返回给调用方的图片链接数量
            log.info("返回图片链接数量: {}", imageUrls.size());
            return String.join(",", imageUrls);
        } catch (Exception e) {
            log.error("图片搜索出错", e); // 记录错误日志
            return "Error search image: " + e.getMessage();
        }
    }

    /**
     * 搜索中等尺寸的图片列表
     *
     * @param query 搜索关键词
     * @return 中等尺寸图片的URL列表
     */
    public List<String> searchMediumImages(String query) {
        // 设置请求头（包含API密钥）
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", API_KEY);

        // 设置请求参数（仅包含query，可根据文档补充page、per_page等参数）
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        // 可以添加分页参数，例如每次返回10张图片
        params.put("per_page", 10);

        log.info("开始搜索图片，关键词: {}", query); // 打印搜索关键词

        // 发送 GET 请求
        String response = HttpUtil.createGet(API_URL)
                .addHeaders(headers)
                .form(params)
                .execute()
                .body();

        // 解析响应JSON（假设响应结构包含"photos"数组，每个元素包含"medium"字段）
        List<String> imageUrls = JSONUtil.parseObj(response)
                .getJSONArray("photos")
                .stream()
                .map(photoObj -> (JSONObject) photoObj)
                .map(photoObj -> photoObj.getJSONObject("src"))
                .map(photo -> photo.getStr("medium"))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());

        // 打印获取到的图片链接，便于调试
        log.info("搜索到的图片链接: {}", imageUrls);
        return imageUrls;
    }
}
