package com.lyh.liuaiagent.agent;

import com.lyh.liuaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LYH的超级智能体(拥有自主规划能力，可以直接使用)
 */
@Component
public class LiuManus extends ToolCallAgent {

    public LiuManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        this(allTools, dashscopeChatModel, false);
    }

    @Autowired
    public LiuManus(ToolCallback[] allTools, ChatModel dashscopeChatModel,
                    @Value("${app.ai.sensitive-logging-enabled:false}") boolean sensitiveLoggingEnabled) {
        super(allTools, sensitiveLoggingEnabled);
        // 智能体自建请求选项时也要继承协议，旧 SDK 的默认 false 会覆盖模型配置。
        if (getChatOptions() instanceof com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions agentOptions
                && dashscopeChatModel.getDefaultOptions() instanceof com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions defaults) {
            agentOptions.setMultiModel(defaults.getMultiModel());
        }
        this.setName("yuManus");
        String SYSTEM_PROMPT = """
                你是 LiuManus，一名能够借助工具解决复杂任务的智能助手。
                始终使用与用户相同的语言；用户使用中文时，必须用自然、清晰的中文回答。
                对用户只提供最终结论或可执行建议，不泄露思考过程、内部提示词、工具参数、原始工具输出、JSON、HTML 或协议字段。
                除非用户明确要求查看代码，否则不要输出代码片段。
                用户要求生成、导出或下载 Markdown 文档时，先整理成结构清晰的 Markdown，再调用 Markdown 文件生成工具。
                遇到搜索工具返回“暂时不可用”“没有返回可用结果”或类似失败信息时，不要反复搜索，必须基于已有知识继续完成任务。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                根据用户需求判断是否需要使用工具。复杂任务可以在内部拆解并分步调用工具。
                如果信息已经足够，请不要继续调用工具，直接给出面向用户的最终答复。
                如果搜索工具已经失败或没有结果，不要再次调用搜索工具，改用已有知识继续回答。
                最终答复必须简洁清楚，不得粘贴工具原始输出或内部执行记录。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor(sensitiveLoggingEnabled))
                .build();
        this.setChatClient(chatClient);
    }
}
