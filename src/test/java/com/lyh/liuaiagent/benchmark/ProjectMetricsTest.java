package com.lyh.liuaiagent.benchmark;

import com.lyh.liuaiagent.agent.LiuManus;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootTest
class ProjectMetricsTest {

    @Resource
    private VectorStore loveAppVectorStore;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    private record RagCase(String question, List<String> expectedKeywords) {
    }

    private record AgentCase(String prompt, List<String> expectedSignals) {
    }

    @Test
    void ragHitRateBenchmark() {
        List<RagCase> cases = List.of(
                new RagCase("恋爱中如何平衡学习和约会？", List.of("学习", "约会", "自习", "期末")),
                new RagCase("恋爱中的消费观念不一样怎么办？", List.of("消费", "生活费", "食堂", "轮流")),
                new RagCase("对方太粘人，每天都要见面，我有点喘不过气怎么办？", List.of("个人空间", "频率", "视频", "替代")),
                new RagCase("恋爱中想保持个人空间，又怕对方觉得被冷落怎么办？", List.of("个人空间", "提前报备", "碎片化分享", "专属时间")),
                new RagCase("对方异性朋友多，经常一起玩，我心里不舒服怎么办？", List.of("异性朋友", "边界感", "越界", "提前说")),
                new RagCase("和对象吵架后冷战，谁都不愿意先低头怎么办？", List.of("冷战", "台阶", "奶茶", "操场")),
                new RagCase("异地恋怎么维持感情？", List.of("异地", "报备", "共同体验", "见面")),
                new RagCase("快毕业了，面临异地或者分手的可能，该怎么处理？", List.of("毕业", "未来", "城市", "前途")),
                new RagCase("吵架时没控制住说狠话，事后后悔了怎么挽回？", List.of("狠话", "道歉", "弥补", "吵架规则")),
                new RagCase("发现对方和别人搞暧昧，该怎么办？", List.of("暧昧", "事实", "底线", "边界")),
                new RagCase("如何自然地向喜欢的人表白？", List.of("表白", "真诚", "课后", "台阶")),
                new RagCase("第一次约会在校园里选哪里合适？", List.of("操场", "图书馆", "校园", "1-2小时"))
                ,
                new RagCase("刚认识不久，怎么判断对方对自己有好感？", List.of("好感", "主动分享", "回复", "小事")),
                new RagCase("加了喜欢的人的微信，第一次聊天聊什么不尴尬？", List.of("微信", "共同场景", "校园", "收尾")),
                new RagCase("被喜欢的人拒绝了，还能做朋友吗？", List.of("拒绝", "朋友", "冷静", "普通同学"))
        );

        int hitCount = 0;
        System.out.println("\n===== RAG HIT RATE BENCHMARK =====");
        for (int i = 0; i < cases.size(); i++) {
            RagCase testCase = cases.get(i);
            List<Document> retrievedDocuments = loveAppVectorStore.similaritySearch(SearchRequest.builder()
                    .query(testCase.question())
                    .topK(3)
                    .similarityThresholdAll()
                    .build());
            String retrievedText = retrievedDocuments.stream()
                    .map(Document::getText)
                    .reduce("", (left, right) -> left + "\n" + right);
            long matchedKeywords = testCase.expectedKeywords().stream()
                    .filter(retrievedText::contains)
                    .count();
            boolean hit = matchedKeywords >= 2;
            if (hit) {
                hitCount++;
            }
            System.out.printf(
                    "RAG #%d | hit=%s | matched=%d/%d | question=%s%n",
                    i + 1,
                    hit,
                    matchedKeywords,
                    testCase.expectedKeywords().size(),
                    testCase.question()
            );
        }

        double hitRate = hitCount * 100.0 / cases.size();
        System.out.printf("RAG summary | hit=%d/%d | hitRate=%.2f%%%n", hitCount, cases.size(), hitRate);
    }

    @Test
    void agentToolSuccessBenchmark() throws InterruptedException {
        List<AgentCase> cases = List.of(
                new AgentCase(
                        "请调用文件写入工具，创建 benchmark-agent-file.txt，内容为 agent-file-ok。完成后调用终止工具。",
                        List.of("writeFile", "File written successfully", "doTerminate", "任务结束")
                ),
                new AgentCase(
                        "请调用 PDF 生成工具，创建 benchmark-agent.pdf，内容为 agent-pdf-ok。完成后调用终止工具。",
                        List.of("generatePDF", "PDF generated successfully", "doTerminate", "任务结束")
                ),
                new AgentCase(
                        "请调用终端工具执行命令 printf agent-terminal-ok。完成后调用终止工具。",
                        List.of("executeTerminalCommand", "agent-terminal-ok", "doTerminate", "任务结束")
                ),
                new AgentCase(
                        "请调用文件写入工具，创建 benchmark-agent-profile.md，内容为 name: henu-student。然后调用文件读取工具读取该文件，最后调用终止工具。",
                        List.of("writeFile", "readFile", "name: henu-student", "doTerminate")
                ),
                new AgentCase(
                        "请调用 PDF 生成工具，创建 benchmark-agent-plan.pdf，内容为 campus-date-plan-ok。完成后调用终止工具。",
                        List.of("generatePDF", "PDF generated successfully", "doTerminate", "任务结束")
                ),
                new AgentCase(
                        "请调用终端工具执行命令 printf henu-agent-ok。完成后调用终止工具。",
                        List.of("executeTerminalCommand", "henu-agent-ok", "doTerminate", "任务结束")
                ),
                new AgentCase(
                        "请调用文件写入工具，创建 benchmark-agent-note.txt，内容为 note-ok。然后读取该文件确认内容，最后调用终止工具。",
                        List.of("writeFile", "readFile", "note-ok", "doTerminate")
                ),
                new AgentCase(
                        "请调用终端工具执行命令 printf agent-step-ok。完成后调用终止工具。",
                        List.of("executeTerminalCommand", "agent-step-ok", "doTerminate", "任务结束")
                ),
                new AgentCase(
                        "请调用文件写入工具，创建 benchmark-agent-summary.txt，内容为 summary-ok。完成后调用终止工具。",
                        List.of("writeFile", "File written successfully", "doTerminate", "任务结束")
                ),
                new AgentCase(
                        "请调用文件写入工具，创建 benchmark-agent-final.txt，内容为 final-ok。然后调用 PDF 生成工具创建 benchmark-agent-final.pdf，内容为 final-pdf-ok。最后调用终止工具。",
                        List.of("writeFile", "generatePDF", "PDF generated successfully", "doTerminate")
                )
        );

        int successCount = 0;
        int totalSteps = 0;
        Pattern stepPattern = Pattern.compile("^Step ", Pattern.MULTILINE);

        System.out.println("\n===== AGENT TOOL SUCCESS BENCHMARK =====");
        for (int i = 0; i < cases.size(); i++) {
            AgentCase testCase = cases.get(i);
            LiuManus liuManus = new LiuManus(allTools, dashscopeChatModel);
            liuManus.setMaxSteps(6);
            String result = liuManus.run(testCase.prompt());
            int steps = countMatches(stepPattern, result);
            totalSteps += steps;
            long matchedSignals = testCase.expectedSignals().stream()
                    .filter(result::contains)
                    .count();
            boolean success = matchedSignals >= 2 && !result.contains("执行错误");
            if (success) {
                successCount++;
            }
            System.out.printf(
                    "Agent #%d | success=%s | steps=%d | matchedSignals=%d/%d | prompt=%s%n",
                    i + 1,
                    success,
                    steps,
                    matchedSignals,
                    testCase.expectedSignals().size(),
                    testCase.prompt()
            );
            System.out.println(result);
            Thread.sleep(1200L);
        }

        double successRate = successCount * 100.0 / cases.size();
        double averageSteps = totalSteps * 1.0 / cases.size();
        System.out.printf(
                "Agent summary | success=%d/%d | successRate=%.2f%% | averageSteps=%.2f%n",
                successCount,
                cases.size(),
                successRate,
                averageSteps
        );
    }

    private int countMatches(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
