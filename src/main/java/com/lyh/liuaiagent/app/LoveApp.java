package com.lyh.liuaiagent.app;


import com.lyh.liuaiagent.advisor.MyLoggerAdvisor;
import com.lyh.liuaiagent.advisor.ReReadingAdvisor;
import com.lyh.liuaiagent.chatmemory.FileBasedChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Component
@Slf4j
public class LoveApp {

    private final ChatClient chatClient;

    record LoveReport(String title, List<String> suggestions) {
    }


    private static final String SYSTEM_PROMPT="你是大学生情感顾问“小爱”，擅长用校园里的轻松语气（可加“呀”“～”这类词）聊恋爱问题。\n" +
            "\n" +
            "核心原则：先问细节，再给办法。\n" +
            "1. 用户说困扰后，先抛1-2个具体问题挖细节，比如：\n" +
            "   - 对方是同学/社团认识的？平时你们会聊些什么呀？\n" +
            "   - 吵架是因为哪件事呢？之前他/她生气时你怎么哄的？\n" +
            "2. 了解清楚后，给具体、能落地的建议（结合校园场景，比如操场、食堂、考试周这些），别说空话。\n" +
            "3. 说完建议补一句：“还有啥没说到的？可以再跟我讲讲～”\n" +
            "\n" +
            "注意：不说教，不评判，遇到“被贬低、太委屈”这类情况，温和提醒：“健康的恋爱是舒服的，实在难受可以找学校心理老师聊聊呀～”";

    public LoveApp(ChatModel dashscopeChatModel) {
        //初始化基于内存的对话记忆
        //ChatMemory chatMemory=new InMemoryChatMemory();
        // 初始化基于文件的对话记忆
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);

        chatClient= ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        //自定义日志 advisor 可按需开启
                        new MyLoggerAdvisor()
                        //ReReadingAdvisor拦截器
                        //new ReReadingAdvisor()
                )
                .build();
    }



    /**
     * AI 基础对话(支持多轮会话记忆)
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message,String chatId){
        ChatResponse response=chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                        .call()
                .chatResponse();
        String content=response.getResult().getOutput().getText();
        log.info("content:{}",content);
        return content;
    }

    /**
     * AI 恋爱报告功能(实战结构化输出)
     * @param message
     * @param chatId
     * @return
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", loveReport);
        return loveReport;
    }


}
