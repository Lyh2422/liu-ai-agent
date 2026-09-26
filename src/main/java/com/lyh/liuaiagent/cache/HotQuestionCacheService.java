package com.lyh.liuaiagent.cache;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/** 单实例、有界、按用户和实际上下文隔离的完整回答缓存。 */
@Component
public class HotQuestionCacheService {
    private static final int HOT_THRESHOLD = 3;
    private static final int REQUIRED_IDENTICAL_COMPLETIONS = 2;
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration HOT_WINDOW = Duration.ofHours(1);
    public static final int MAX_ANSWER_CHARS = 20_000;
    private final Clock clock;
    private final int maxEntries;
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, .75f, true);

    public HotQuestionCacheService() { this(Clock.systemUTC(), 1000); }

    HotQuestionCacheService(Clock clock, int maxEntries) {
        if (maxEntries < 1) throw new IllegalArgumentException("缓存容量必须为正数");
        this.clock = clock;
        this.maxEntries = maxEntries;
    }

    public record Key(String digest) {}

    public Key keyFor(Long userId, String appType, String knowledgeVersion, List<Message> history, String question) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // 提示词与证据规则变化时升级版本，避免复用旧策略生成的回答。
            addField(digest, "love-answer-grounded-v2");
            addField(digest, userId.toString());
            addField(digest, appType);
            addField(digest, knowledgeVersion);
            for (Message message : history) {
                addField(digest, message.getMessageType().name());
                addField(digest, message.getText());
            }
            addField(digest, "current-user-question");
            // 回答缓存采用保守精确匹配，保留标点、大小写和数字小数点，避免不同问题碰撞。
            addField(digest, question.strip());
            return new Key(Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static void addField(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    public synchronized int recordQuestion(Key key) {
        purgeExpired();
        long now = clock.millis();
        Entry entry = entries.computeIfAbsent(key, ignored -> new Entry(now));
        if (now - entry.windowStartedAt >= HOT_WINDOW.toMillis()) {
            entry.windowStartedAt = now;
            entry.count = 0;
        }
        entry.count = Math.min(HOT_THRESHOLD, entry.count + 1);
        while (entries.size() > maxEntries) entries.remove(entries.keySet().iterator().next());
        return entry.count;
    }

    public synchronized Optional<String> getCachedAnswer(Key key) {
        purgeExpired();
        Entry entry = entries.get(key);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.answer);
    }

    public synchronized boolean cacheAnswerIfHot(Key key, String answer) {
        purgeExpired();
        Entry entry = entries.get(key);
        if (entry == null || clock.millis() - entry.windowStartedAt >= HOT_WINDOW.toMillis()
                || answer == null || answer.isBlank() || answer.length() > MAX_ANSWER_CHARS) return false;
        if (answer.equals(entry.candidateAnswer)) {
            entry.identicalCompletions++;
        } else {
            entry.candidateAnswer = answer;
            entry.identicalCompletions = 1;
        }
        if (entry.count < HOT_THRESHOLD || entry.identicalCompletions < REQUIRED_IDENTICAL_COMPLETIONS) return false;
        entry.answer = answer;
        entry.answerExpiresAt = clock.millis() + CACHE_TTL.toMillis();
        return true;
    }

    private void purgeExpired() {
        long now = clock.millis();
        entries.values().removeIf(entry -> {
            if (now >= entry.answerExpiresAt) entry.answer = null;
            return entry.answer == null && now - entry.windowStartedAt >= HOT_WINDOW.toMillis();
        });
    }

    private static final class Entry {
        private int count;
        private long windowStartedAt;
        private String answer;
        private long answerExpiresAt;
        private String candidateAnswer;
        private int identicalCompletions;
        private Entry(long now) { windowStartedAt = now; }
    }
}
