package com.lyh.liuaiagent.cache;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HotQuestionCacheServiceTest {
    private final MutableClock clock = new MutableClock();
    private final HotQuestionCacheService cache = new HotQuestionCacheService(clock, 2);
    private HotQuestionCacheService.Key key(String question) { return cache.keyFor(1L, "LOVE", "v1", List.of(), question); }
    private void warm(HotQuestionCacheService.Key key) { for (int i = 0; i < 3; i++) cache.recordQuestion(key); }

    @Test void storesOnlyHotCompleteNonemptyAnswers() {
        var key = key("怎么沟通？");
        assertEquals(1, cache.recordQuestion(key));
        assertEquals(2, cache.recordQuestion(key));
        assertFalse(cache.cacheAnswerIfHot(key, "回答"));
        assertEquals(3, cache.recordQuestion(key));
        assertFalse(cache.cacheAnswerIfHot(key, " "));
        assertFalse(cache.cacheAnswerIfHot(key, "a".repeat(20_001)));
        assertTrue(cache.cacheAnswerIfHot(key, "完整回答"));
        assertEquals("完整回答", cache.getCachedAnswer(key).orElseThrow());
    }

    @Test void isolatesUsersAppsHistoryRolesAndKnowledgeVersions() {
        var original = key("怎么办？");
        assertNotEquals(original, cache.keyFor(2L, "LOVE", "v1", List.of(), "怎么办？"));
        assertNotEquals(original, cache.keyFor(1L, "MANUS", "v1", List.of(), "怎么办？"));
        assertNotEquals(original, cache.keyFor(1L, "LOVE", "v2", List.of(), "怎么办？"));
        var withHistory = cache.keyFor(1L, "LOVE", "v1", List.of(new UserMessage("喜欢的人")), "怎么办？");
        assertNotEquals(original, withHistory);
        assertNotEquals(withHistory, cache.keyFor(1L, "LOVE", "v1", List.of(new AssistantMessage("喜欢的人")), "怎么办？"));
        assertNotEquals(withHistory, cache.keyFor(1L, "LOVE", "v1", List.of(new UserMessage("我的前任")), "怎么办？"));
    }

    @Test void keepsMeaningfulPunctuationAndWordBoundaries() {
        assertNotEquals(key("1.5元"), key("15元"));
        assertNotEquals(key("a b"), key("ab"));
        assertNotEquals(key("不分手"), key("分手"));
        assertEquals(key("  怎么办？  "), key("怎么办？"));
    }

    @Test void expiresAfterThirtyMinutesWithoutExtendingOnRead() {
        var key = key("问题"); warm(key); cache.cacheAnswerIfHot(key, "回答");
        clock.advance(Duration.ofMinutes(29));
        assertTrue(cache.getCachedAnswer(key).isPresent());
        clock.advance(Duration.ofMinutes(1));
        assertTrue(cache.getCachedAnswer(key).isEmpty());
    }

    @Test void resetsPopularityAfterTheWindow() {
        var key = key("问题"); warm(key);
        clock.advance(Duration.ofHours(1));
        assertFalse(cache.cacheAnswerIfHot(key, "过时回答"));
        assertEquals(1, cache.recordQuestion(key));
    }

    @Test void evictsLeastRecentlyUsedEntriesIncludingPopularityStats() {
        var first = key("第一问"); var second = key("第二问"); var third = key("第三问");
        warm(first); cache.cacheAnswerIfHot(first, "第一答");
        warm(second); cache.cacheAnswerIfHot(second, "第二答");
        cache.getCachedAnswer(first);
        cache.recordQuestion(third);
        assertTrue(cache.getCachedAnswer(first).isPresent());
        assertTrue(cache.getCachedAnswer(second).isEmpty());
        assertFalse(cache.cacheAnswerIfHot(second, "不该写入"));
    }

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-16T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
