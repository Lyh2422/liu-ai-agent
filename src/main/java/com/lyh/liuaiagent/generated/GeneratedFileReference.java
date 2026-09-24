package com.lyh.liuaiagent.generated;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 用户可下载生成文件在 Agent 内部使用的稳定引用格式。 */
public record GeneratedFileReference(String id, String filename) {
    private static final Pattern MARKER = Pattern.compile(
            "\\[\\[generated-file:([0-9a-fA-F-]{36}):([^]\\r\\n]+)]]");

    public String marker() {
        return "[[generated-file:" + id + ":" + filename + "]]";
    }

    public static List<GeneratedFileReference> findAll(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<GeneratedFileReference> references = new ArrayList<>();
        Matcher matcher = MARKER.matcher(value);
        while (matcher.find()) {
            references.add(new GeneratedFileReference(matcher.group(1), matcher.group(2)));
        }
        return references;
    }

    public static String removeMarkers(String value) {
        return value == null ? "" : MARKER.matcher(value).replaceAll("").stripTrailing();
    }
}
