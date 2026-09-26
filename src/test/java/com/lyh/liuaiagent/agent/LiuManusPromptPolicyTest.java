package com.lyh.liuaiagent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LiuManusPromptPolicyTest {

    @Test
    void requiresEvidenceForCurrentFactsAndAbstainsWhenSearchFails() {
        var agent = new LiuManus(new ToolCallback[0], mock(ChatModel.class));

        assertTrue(agent.getSystemPrompt().contains("必须先用工具取得可靠来源"));
        assertTrue(agent.getSystemPrompt().contains("不得用模型记忆补写"));
        assertTrue(agent.getSystemPrompt().contains("事实必须有工具结果支持"));
        assertTrue(agent.getNextStepPrompt().contains("搜索摘要本身不能作为最终事实依据"));
    }
}
