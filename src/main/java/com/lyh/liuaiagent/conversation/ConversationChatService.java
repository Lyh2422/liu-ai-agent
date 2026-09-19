package com.lyh.liuaiagent.conversation;

import com.lyh.liuaiagent.agent.LiuManus;
import com.lyh.liuaiagent.app.LoveApp;
import com.lyh.liuaiagent.cache.HotQuestionCacheService;
import com.lyh.liuaiagent.knowledge.KnowledgeIndex;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;
import java.util.ArrayList;

@Service
@lombok.extern.slf4j.Slf4j
public class ConversationChatService {
    private final ConversationStore store;
    private final LoveApp loveApp;
    private final ToolCallback[] tools;
    private final ChatModel model;
    private final HotQuestionCacheService cache;
    private final KnowledgeIndex knowledge;

    public ConversationChatService(ConversationStore store, LoveApp loveApp, ToolCallback[] allTools, ChatModel dashscopeChatModel,
                                   HotQuestionCacheService cache, KnowledgeIndex knowledge) {
        this.store = store;
        this.loveApp = loveApp;
        this.tools = allTools;
        this.model = dashscopeChatModel;
        this.cache = cache;
        this.knowledge = knowledge;
    }

    public Flux<ServerSentEvent<String>> stream(Long userId, String id, Conversation.AppType appType, String message) {
        // 在返回 SSE 前校验归属和并发状态，让非法请求得到正常的 HTTP 404 / 409。
        var turn = store.begin(userId, id, appType, message);
        // concat 先发送 ack，再订阅延迟构建的慢链路；检索、模型和缓存查找均不阻塞 ack。
        return Flux.concat(Flux.just(event("ack", "thinking")), Flux.defer(() -> {
            String version = knowledge.cacheVersion();
            HotQuestionCacheService.Key key = appType == Conversation.AppType.LOVE
                    ? cache.keyFor(userId, appType.name(), version, turn.history(), message) : null;
            var cached = java.util.Optional.<String>empty();
            if (key != null) {
                cache.recordQuestion(key);
                cached = cache.getCachedAnswer(key);
            }
            boolean cacheHit = cached.isPresent();
            StringBuilder completedAnswer = new StringBuilder();
            Flux<String> answer;
            if (cacheHit) {
                answer = Flux.just(cached.orElseThrow());
            } else if (appType == Conversation.AppType.LOVE) {
                answer = loveApp.chatWithHistory(message, turn.history());
            } else {
                LiuManus agent = new LiuManus(tools, model);
                agent.setMessageList(new ArrayList<>(turn.history()));
                answer = agent.runStreamEvents(message);
            }
            return Flux.concat(answer
                    .timeout(Duration.ofMinutes(5))
                    .publishOn(Schedulers.boundedElastic())
                    .filter(chunk -> !chunk.isEmpty())
                    .map(chunk -> {
                        // 先提交数据库再发送片段；刷新、断线或重启后可读取已显示的内容。
                        store.append(turn.id(), chunk);
                        // 超过缓存容量的回复仍正常流式保存，停止额外累积大字符串。
                        int remaining = HotQuestionCacheService.MAX_ANSWER_CHARS + 1 - completedAnswer.length();
                        if (remaining > 0) completedAnswer.append(chunk, 0, Math.min(chunk.length(), remaining));
                        return event("delta", chunk);
                    }), Flux.defer(() -> {
                        store.finish(turn.id(), ChatTurn.Status.COMPLETED);
                        // 只有成功完成并持久化的回答可入缓存；知识更新中的旧回复不入缓存。
                        if (key != null && !cacheHit && version.equals(knowledge.cacheVersion())) {
                            cache.cacheAnswerIfHot(key, completedAnswer.toString());
                        }
                        return Flux.just(event("done", "completed"));
                    }));
        }).subscribeOn(Schedulers.boundedElastic())).onErrorResume(error -> {
            log.warn("会话 {} 生成失败", id, error);
            store.finish(turn.id(), ChatTurn.Status.FAILED);
            return Flux.just(event("error", "回复失败，已保存现有内容，请稍后重试"));
        }).doOnCancel(() -> store.finish(turn.id(), ChatTurn.Status.INTERRUPTED));
    }

    private static ServerSentEvent<String> event(String name, String content) {
        return ServerSentEvent.<String>builder().event(name).data(content).build();
    }
}
