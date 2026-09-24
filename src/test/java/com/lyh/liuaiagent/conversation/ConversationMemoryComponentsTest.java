package com.lyh.liuaiagent.conversation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMemoryComponentsTest {
    private final ContextTokenEstimator tokens = new ContextTokenEstimator();
    private final ExplicitUserFactExtractor facts = new ExplicitUserFactExtractor();

    @Test void estimatorHandlesChineseAsciiAndNeverTruncatesPastBudget() {
        assertEquals(4, tokens.estimate("你好ABCD12"));
        String truncated = tokens.truncate("你好世界abcdef", 4);
        assertTrue(tokens.estimate(truncated) <= 4);
        assertTrue(truncated.endsWith("…"));
    }

    @Test void extractorOnlyStoresSupportedExplicitFirstPersonFacts() {
        var extracted = facts.extract("我叫小林，我学的是计算机专业。我对花生过敏，我喜欢羽毛球。");
        assertEquals(4, extracted.size());
        assertTrue(extracted.stream().anyMatch(fact -> fact.type().equals("NAME") && fact.value().equals("小林")));
        assertTrue(extracted.stream().anyMatch(fact -> fact.type().equals("MAJOR") && fact.value().equals("计算机")));
        assertTrue(extracted.stream().anyMatch(fact -> fact.type().equals("ALLERGY") && fact.value().equals("花生")));
        assertTrue(extracted.stream().anyMatch(fact -> fact.type().equals("LIKE") && fact.value().equals("羽毛球")));
        assertTrue(facts.extract("你看起来应该喜欢羽毛球").isEmpty());
    }
}
