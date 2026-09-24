package com.lyh.liuaiagent.conversation;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 只提取用户以第一人称明确表达的少量事实，避免把模型猜测写入长期记忆。 */
@Component
public class ExplicitUserFactExtractor {
    private static final Pattern NAME = Pattern.compile("(?:我叫|我的名字(?:叫|是))\\s*([\\p{IsHan}A-Za-z0-9·_-]{1,20})");
    private static final Pattern MAJOR = Pattern.compile("(?:我是|我读|我学的是?)\\s*([^，。！？,!?\\n]{1,30})专业");
    private static final Pattern ALLERGY = Pattern.compile("我对\\s*([^，。！？,!?\\n]{1,30})\\s*过敏");
    private static final Pattern DISLIKE = Pattern.compile("我(?:不喜欢|讨厌)\\s*([^，。！？,!?\\n]{1,40})");
    private static final Pattern LIKE = Pattern.compile("我喜欢\\s*([^，。！？,!?\\n]{1,40})");

    public record Candidate(String type, String key, String value, double confidence) {}

    public List<Candidate> extract(String message) {
        if (message == null || message.isBlank()) return List.of();
        List<Candidate> facts = new ArrayList<>();
        addSingle(facts, NAME, message, "NAME", "name", 1.0);
        addSingle(facts, MAJOR, message, "MAJOR", "major", .98);
        addMany(facts, ALLERGY, message, "ALLERGY", "allergy", 1.0);
        addMany(facts, DISLIKE, message, "DISLIKE", "dislike", .95);
        addMany(facts, LIKE, message, "LIKE", "like", .95);
        return List.copyOf(facts);
    }

    private static void addSingle(List<Candidate> facts, Pattern pattern, String message,
                                  String type, String key, double confidence) {
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) facts.add(new Candidate(type, key, normalize(matcher.group(1)), confidence));
    }

    private static void addMany(List<Candidate> facts, Pattern pattern, String message,
                                String type, String keyPrefix, double confidence) {
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String value = normalize(matcher.group(1));
            if (!value.isBlank()) facts.add(new Candidate(type, keyPrefix + ":" + digest(value), value, confidence));
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip();
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, 16);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 not available", error);
        }
    }
}
