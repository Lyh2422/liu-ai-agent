package com.lyh.liuaiagent.conversation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RelationshipSummaryTest {
    private static ChatTurn turn(long id, String user) {
        var turn = new ChatTurn();
        turn.setId(id);
        turn.setUserContent(user);
        turn.setAssistantContent("建议你马上分手，这一定是对方的错");
        return turn;
    }

    @Test void scansEntireMessageAndPreservesCategoriesAndEvidenceWithoutAssistantGuesses() {
        String summary = RelationshipSummary.update("", List.of(turn(10,
                "我们异地两年。上周因为失约吵架。我担心关系变淡。" + "普通描述。".repeat(80)
                        + "我想先修复关系。试过认真道歉但没有用。")), 6000);
        for (String category : List.of("关系背景", "关键事件", "当前困扰", "用户目标", "沟通反馈")) {
            assertTrue(summary.contains("【" + category + "】"), summary);
        }
        assertTrue(summary.contains("我想先修复关系"));
        assertTrue(summary.contains("[turn=10]"));
        assertFalse(summary.contains("马上分手"));
    }

    @Test void newerStatementsLeadEachCategoryAndEveryCategorySurvivesContextBudget() {
        String summary = RelationshipSummary.update("", List.of(turn(1, "我们异地两年。昨天吵架。我担心沟通。")), 6000);
        summary = RelationshipSummary.update(summary, List.of(turn(2, "我们现在在同一个城市恋爱。我想认真沟通。尝试谈心后好转。")), 6000);
        assertTrue(summary.indexOf("现在在同一个城市") < summary.indexOf("异地两年"));
        var tokens = new ContextTokenEstimator();
        String context = RelationshipSummary.forContext(summary, tokens, 700);
        assertTrue(context.contains("【沟通反馈】"));
        assertTrue(context.contains("【关系背景】"));
        assertTrue(tokens.estimate(context) <= 700);
        assertTrue(summary.length() <= 6000);
    }

    @Test void legacySummaryCanBeReadWithoutReintroducingAssistantContent() {
        String summary = RelationshipSummary.forContext("- 用户曾说：我很担心；助手：你应该分手", new ContextTokenEstimator(), 300);
        assertTrue(summary.contains("我很担心"));
        assertFalse(summary.contains("你应该分手"));
    }
}
