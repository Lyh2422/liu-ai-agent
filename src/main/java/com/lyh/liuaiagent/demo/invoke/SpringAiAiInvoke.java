package com.lyh.liuaiagent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Spring AI 框架 调用AI大模型
 */
@Component
@Profile("ai-invoke-demo")
public class SpringAiAiInvoke implements CommandLineRunner {

    @Resource
    private ChatModel dashscopeChatModel;

    @Override
    public void run(String... args) throws Exception {
        AssistantMessage output = dashscopeChatModel.call(new Prompt("你好，我是刘远浩"))
                .getResult()
                .getOutput();
        System.out.println(output.getText());
    }
}
