package com.lyh.liuaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoveAppContextualQueryAugmenterTest {

    private final LoveAppContextualQueryAugmenter augmenter = new LoveAppContextualQueryAugmenter();

    @Test
    void keepsTheOriginalQuestionAndAddsSafeFallbackRulesWhenRetrievalMisses() {
        List<Message> history = List.of(new UserMessage("我叫小林"));
        var query = new Query("学校心理中心周末几点开门？", history, Map.of("conversationId", "c1"));

        Query augmented = augmenter.augment(query, List.of());

        assertTrue(augmented.text().contains(LoveAppContextualQueryAugmenter.EMPTY_CONTEXT_MARKER));
        assertTrue(augmented.text().contains("学校心理中心周末几点开门？"));
        assertTrue(augmented.text().contains("学校官方渠道核实"));
        assertTrue(augmented.text().contains("不要编造"));
        assertEquals(history, augmented.history());
        assertEquals(query.context(), augmented.context());
    }

    @Test
    void usesGroundedRulesAndKnowledgeTextWhenRetrievalHits() {
        List<Message> history = List.of(new UserMessage("我们是同班同学"));
        var query = new Query("冷战以后怎么开口？", history, Map.of("conversationId", "c2"));
        var document = Document.builder().id("repair").text("先暂停情绪，再约一个双方都有空的时间沟通。")
                .metadata(Map.of("filename", "冲突修复.md")).build();

        Query augmented = augmenter.augment(query, List.of(document));

        assertTrue(augmented.text().contains(document.getText()));
        assertTrue(augmented.text().contains(query.text()));
        assertTrue(augmented.text().contains("不要使用“根据知识库”"));
        assertFalse(augmented.text().contains(LoveAppContextualQueryAugmenter.EMPTY_CONTEXT_MARKER));
        assertEquals(history, augmented.history());
        assertEquals(query.context(), augmented.context());
    }
}
