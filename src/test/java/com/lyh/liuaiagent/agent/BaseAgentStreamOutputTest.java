package com.lyh.liuaiagent.agent;

import com.lyh.liuaiagent.agent.model.AgentState;
import com.lyh.liuaiagent.generated.GeneratedFileReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaseAgentStreamOutputTest {

    @Test
    void streamsOnlyFinalAnswerAndKeepsIntermediateToolOutputInternal() {
        BaseAgent agent = new BaseAgent() {
            private int step;

            @Override
            public String step() {
                step++;
                if (step == 1) {
                    return "Tool searchWeb result: <html><script>internal()</script></html>";
                }
                setState(AgentState.FINISHED);
                return "这是整理后的中文答复。";
            }
        };

        List<String> chunks = agent.runStreamEvents("请回答问题").collectList().block();

        assertEquals(List.of("这是整理后的中文答复。"), chunks);
    }

    @Test
    void letsToolAgentsReplaceTerminationTraceWithUserFacingAnswer() {
        BaseAgent agent = new BaseAgent() {
            @Override
            public String step() {
                setState(AgentState.FINISHED);
                return "工具 doTerminate 完成了它的任务！结果: 任务结束";
            }

            @Override
            protected String buildUserFacingResult(String lastStepResult, boolean reachedStepLimit) {
                return "任务已完成，结果已经整理好。";
            }
        };

        List<String> chunks = agent.runStreamEvents("执行任务").collectList().block();

        assertEquals(List.of("任务已完成，结果已经整理好。"), chunks);
    }

    @Test
    void toolCallAgentSummarizesTerminationTraceInsteadOfReturningIt() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(client.prompt(any(Prompt.class)).system(anyString()).call().chatResponse())
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("文件已经创建完成。")))));
        ToolCallAgent agent = new ToolCallAgent(new ToolCallback[0]);
        agent.setChatClient(client);
        agent.setSystemPrompt("使用中文回答");

        String answer = agent.buildUserFacingResult(
                "工具 doTerminate 完成了它的任务！结果: 任务结束", false);

        assertEquals("文件已经创建完成。", answer);
    }

    @Test
    void toolCallAgentKeepsGeneratedFileReferenceInFinalAnswer() {
        ToolCallAgent agent = new ToolCallAgent(new ToolCallback[0]);
        GeneratedFileReference reference = new GeneratedFileReference(
                "00000000-0000-0000-0000-000000000001", "学习计划.md");
        agent.getGeneratedFiles().add(reference);
        agent.setFinalAnswer("学习计划已经整理完成。" + reference.marker());

        String answer = agent.buildUserFacingResult("内部工具输出", false);

        assertEquals("学习计划已经整理完成。\n\n" + reference.marker(), answer);
    }
}
