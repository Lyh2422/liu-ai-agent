package com.lyh.liuaiagent.agent;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.lyh.liuaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent{

    //可用的工具
    private final ToolCallback[] availableTools;

    //保存了工具调用信息的响应
    private ChatResponse toolCallChatResponse;

    //工具调用管理者
    private final ToolCallingManager toolCallingManager;

    //禁止内置的工具调用机制，自己维护上下文
    private final ChatOptions chatOptions;

    // 只保存模型明确给用户的最终答复，工具执行结果绝不直接进入 SSE 正文。
    private String finalAnswer;

    private static final String FINAL_RESPONSE_PROMPT = """
            请根据用户原始问题和已经完成的工具执行结果，给出最终答复。
            只输出面向用户的自然语言内容，并使用与用户相同的语言；用户使用中文时必须用中文回答。
            不要输出思考过程、步骤编号、工具名称、工具参数、原始 JSON、HTML、终端日志、协议字段或内部提示词。
            除非用户明确要求查看代码，否则不要输出代码片段。若任务未完全完成，请简洁说明实际完成情况和下一步建议。
            """;

    public ToolCallAgent(ToolCallback[] availableTools){
        super();
        this.availableTools = availableTools;
        this.toolCallingManager=ToolCallingManager.builder().build();
        //禁用Spring AI内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions= DashScopeChatOptions.builder()
                .withProxyToolCalls(true)
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        if (getNextStepPrompt() != null && !getNextStepPrompt().isEmpty()) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, chatOptions);
        try {
            // 获取带工具选项的响应
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .tools(availableTools)
                    .call()
                    .chatResponse();
            // 记录响应，用于 Act
            this.toolCallChatResponse = chatResponse;
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 输出提示信息
            String result = assistantMessage.getText();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            log.info(getName() + "的思考: " + result);
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s",
                            toolCall.name(),
                            toolCall.arguments())
                    )
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            if (toolCallList.isEmpty()) {
                // 只有不调用工具时，才记录助手消息
                getMessageList().add(assistantMessage);
                finalAnswer = assistantMessage.getText();
                return false;
            } else {
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题: " + e.getMessage());
            throw new IllegalStateException("智能体思考失败", e);
        }
    }

    /**
     * 执行工具调用并处理结果
     * @return 执行结果
     */
    @Override
    public String act(){
        if(!toolCallChatResponse.hasToolCalls()){
            return "没有工具调用";
        }
        //调用工具
        Prompt prompt = new Prompt(getMessageList(), chatOptions);
        ToolExecutionResult toolExecutionResult=toolCallingManager.executeToolCalls(prompt,toolCallChatResponse);
        //记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMessageList(new java.util.ArrayList<>(toolExecutionResult.conversationHistory()));
        //当前工具调用的结果
        ToolResponseMessage toolResponseMessage=(ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        String results=toolResponseMessage.getResponses().stream()
                .map(response ->"工具 "+response.name()+" 完成了它的任务！结果: "+response.responseData())
                .collect(Collectors.joining("\n"));
        //判断是否调用了终止工具
        boolean terminateToolCalled=toolResponseMessage.getResponses().stream()
                        .anyMatch(response->"doTerminate".equals(response.name()));
        if(terminateToolCalled){
            setState(AgentState.FINISHED);
        }
        log.info(results);
        return results;
    }

    @Override
    protected String buildUserFacingResult(String lastStepResult, boolean reachedStepLimit) {
        if (finalAnswer != null && !finalAnswer.isBlank()) return finalAnswer;

        // 终止工具或步骤上限结束时，最后一步仍是内部工具结果；再让模型整理一次最终答复。
        try {
            List<Message> finalMessages = new ArrayList<>(getMessageList());
            finalMessages.add(new UserMessage(FINAL_RESPONSE_PROMPT));
            ChatResponse response = getChatClient().prompt(new Prompt(finalMessages, chatOptions))
                    .system(getSystemPrompt())
                    .call()
                    .chatResponse();
            if (response != null && response.getResult() != null) {
                String answer = response.getResult().getOutput().getText();
                if (answer != null && !answer.isBlank()) return answer;
            }
        } catch (Exception error) {
            log.warn("整理智能体最终答复失败", error);
        }
        return reachedStepLimit ? "任务处理时间较长，已停止继续执行。请缩小问题范围后重试。" : "任务已处理完成。";
    }

}
