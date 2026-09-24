package com.lyh.liuaiagent.conversation;

import com.lyh.liuaiagent.agent.LiuManus;
import com.lyh.liuaiagent.app.LoveApp;
import com.lyh.liuaiagent.cache.HotQuestionCacheService;
import com.lyh.liuaiagent.knowledge.KnowledgeIndex;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@lombok.extern.slf4j.Slf4j
public class ConversationChatService {
    private static final int STREAM_BATCH_MAX_CHUNKS = 12;
    private static final Duration STREAM_BATCH_MAX_WAIT = Duration.ofMillis(80);
    private final ConversationStore store;
    private final LoveApp loveApp;
    private final ToolCallback[] tools;
    private final ChatModel model;
    private final HotQuestionCacheService cache;
    private final KnowledgeIndex knowledge;
    private final boolean sensitiveLoggingEnabled;

    public ConversationChatService(ConversationStore store, LoveApp loveApp, ToolCallback[] allTools, ChatModel dashscopeChatModel,
                                   HotQuestionCacheService cache, KnowledgeIndex knowledge) {
        this(store, loveApp, allTools, dashscopeChatModel, cache, knowledge, false);
    }

    @Autowired
    public ConversationChatService(ConversationStore store, LoveApp loveApp, ToolCallback[] allTools, ChatModel dashscopeChatModel,
                                   HotQuestionCacheService cache, KnowledgeIndex knowledge,
                                   @Value("${app.ai.sensitive-logging-enabled:false}") boolean sensitiveLoggingEnabled) {
        this.store = store;
        this.loveApp = loveApp;
        this.tools = allTools;
        this.model = dashscopeChatModel;
        this.cache = cache;
        this.knowledge = knowledge;
        this.sensitiveLoggingEnabled = sensitiveLoggingEnabled;
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
                LiuManus agent = new LiuManus(tools, model, sensitiveLoggingEnabled);
                agent.setMessageList(new ArrayList<>(turn.history()));
                answer = agent.runStreamEvents(message);
            }
            return Flux.concat(persistInBatches(turn.id(), answer
                    .timeout(Duration.ofMinutes(5))
                    .publishOn(Schedulers.boundedElastic())
                    .filter(chunk -> !chunk.isEmpty()))
                    .map(chunk -> {
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
            if (sensitiveLoggingEnabled) {
                log.warn("会话 {} 生成失败", id, error);
            } else {
                log.warn("会话 {} 生成失败（{}）", id, error.getClass().getSimpleName());
            }
            store.finish(turn.id(), ChatTurn.Status.FAILED);
            return Flux.just(event("error", "回复失败，已保存现有内容，请稍后重试"));
        }).doOnCancel(() -> store.finish(turn.id(), ChatTurn.Status.INTERRUPTED));
    }

    /**
     * 把模型碎片按数量或短时间窗口合并。错误被物化成普通信号，确保错误前最后一批文本
     * 仍会先写库、再发给客户端，然后再恢复原始错误交给统一失败处理。
     */
    private Flux<String> persistInBatches(Long turnId, Flux<String> answer) {
        return answer.materialize()
                .bufferTimeout(STREAM_BATCH_MAX_CHUNKS, STREAM_BATCH_MAX_WAIT)
                .concatMap(signals -> persistBatch(turnId, signals));
    }

    private Flux<String> persistBatch(Long turnId, List<Signal<String>> signals) {
        StringBuilder content = new StringBuilder();
        Throwable failure = null;
        for (Signal<String> signal : signals) {
            if (signal.isOnNext() && signal.get() != null) content.append(signal.get());
            if (signal.isOnError()) failure = signal.getThrowable();
        }
        Flux<String> persisted = Flux.empty();
        if (!content.isEmpty()) {
            String batch = content.toString();
            // 数据库提交发生在 onNext 之前，客户端看到的每个 delta 都已有可恢复副本。
            persisted = Mono.fromCallable(() -> {
                store.append(turnId, batch);
                return batch;
            }).flux();
        }
        return failure == null ? persisted : persisted.concatWith(Flux.error(failure));
    }

    private static ServerSentEvent<String> event(String name, String content) {
        return ServerSentEvent.<String>builder().event(name).data(content).build();
    }
}
