package com.lyh.liuaiagent.conversation;

import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/** 排在 RAG 之后，对最终文本请求计数；完整保留系统规则和当前用户原文。 */
public final class FinalPromptBudgetAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {
    public static final String CURRENT_MESSAGE = "conversation.current-user-message";
    private final ContextTokenEstimator tokens = new ContextTokenEstimator();
    private final int limit;

    public FinalPromptBudgetAdvisor(int inputBudget, int safetyMargin) {
        if (inputBudget < 1 || safetyMargin < 0 || safetyMargin >= inputBudget) {
            throw new IllegalArgumentException("最终上下文预算配置无效");
        }
        limit = inputBudget - safetyMargin;
    }

    @Override public String getName() { return getClass().getSimpleName(); }
    @Override public int getOrder() { return Integer.MAX_VALUE - 100; }

    @Override public AdvisedResponse aroundCall(AdvisedRequest request, CallAroundAdvisorChain chain) {
        return chain.nextAroundCall(bound(request));
    }

    @Override public Flux<AdvisedResponse> aroundStream(AdvisedRequest request, StreamAroundAdvisorChain chain) {
        return Flux.defer(() -> chain.nextAroundStream(bound(request)));
    }

    private AdvisedRequest bound(AdvisedRequest request) {
        if (cost(request) <= limit) return request;
        String original = (String) request.advisorParams().get(CURRENT_MESSAGE);
        if (original == null) throw new InvalidConversationRequestException("缺少当前消息，无法安全整理上下文");
        AdvisedRequest bounded = request;
        // 优先撤下本次检索资料，并明确没有可引用资料，避免截断来源却仍要求模型引用。
        if (!request.userText().equals(original)) {
            bounded = AdvisedRequest.from(request).userText("本次参考资料因上下文长度限制未提供。"
                    + "不得编造引用、学校制度或联系方式；可以提供一般性情绪支持和沟通建议。\n用户原问题：" + original)
                    .userParams(java.util.Map.of()).build();
        }
        List<Message> history = new ArrayList<>(bounded.messages());
        while (cost(bounded) > limit) {
            int oldestUser = -1;
            for (int i = 0; i < history.size(); i++) {
                if (history.get(i).getMessageType() == MessageType.USER) { oldestUser = i; break; }
            }
            if (oldestUser < 0) break;
            history.remove(oldestUser);
            if (oldestUser < history.size() && history.get(oldestUser).getMessageType() == MessageType.ASSISTANT) {
                history.remove(oldestUser);
            }
            bounded = AdvisedRequest.from(bounded).messages(List.copyOf(history)).build();
        }
        if (cost(bounded) > limit) {
            // 此客户端的 messages 只包含后端注入的记忆和历史；系统规则位于 systemText，始终保留。
            bounded = AdvisedRequest.from(bounded).messages(List.of()).build();
        }
        if (cost(bounded) > limit) {
            throw new InvalidConversationRequestException("这条消息和必要提示合计过长，请拆成几段发送");
        }
        return bounded;
    }

    private int cost(AdvisedRequest request) {
        return request.toPrompt().getInstructions().stream().mapToInt(tokens::estimate).sum();
    }
}
