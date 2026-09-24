package com.lyh.liuaiagent.conversation;

import com.lyh.liuaiagent.auth.exception.ConflictException;
import com.lyh.liuaiagent.auth.exception.NotFoundException;
import org.springframework.ai.chat.messages.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class ConversationStore {
    private final ConversationRepository conversations;
    private final ChatTurnRepository turns;
    private final ConversationMemoryManager memory;
    public ConversationStore(ConversationRepository conversations, ChatTurnRepository turns,
                             ConversationMemoryManager memory) {
        this.conversations = conversations;
        this.turns = turns;
        this.memory = memory;
    }

    public Conversation create(Long userId, Conversation.AppType appType) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setAppType(appType);
        return conversations.save(conversation);
    }

    @Transactional(readOnly = true)
    public List<Conversation> list(Long userId, Conversation.AppType appType) {
        return conversations.findByUserIdAndAppTypeOrderByUpdatedAtDesc(userId, appType);
    }

    @Transactional(readOnly = true)
    public Conversation owned(Long userId, String id) {
        return conversations.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("会话不存在"));
    }

    public record HistoryMessage(String id, String role, String content, String status, Instant createdAt) {}
    public record Detail(Conversation conversation, List<HistoryMessage> messages) {}

    @Transactional(readOnly = true)
    public Detail detail(Long userId, String id) {
        Conversation conversation = owned(userId, id);
        List<HistoryMessage> messages = new ArrayList<>();
        for (ChatTurn turn : turns.findByConversationIdOrderByIdAsc(id)) {
            messages.add(new HistoryMessage(turn.getId() + "-user", "user", turn.getUserContent(), "COMPLETED", turn.getCreatedAt()));
            messages.add(new HistoryMessage(turn.getId() + "-assistant", "assistant", turn.getAssistantContent(), turn.getStatus().name(), turn.getCreatedAt()));
        }
        return new Detail(conversation, messages);
    }

    public void delete(Long userId, String id) {
        Conversation conversation = conversations.lockOwned(id, userId)
                .orElseThrow(() -> new NotFoundException("会话不存在"));
        if (turns.existsByConversationIdAndStatus(id, ChatTurn.Status.STREAMING)) {
            throw new ConflictException("这个会话正在回复，暂时不能删除");
        }
        memory.deleteForConversation(id);
        turns.deleteByConversationId(id);
        conversations.delete(conversation);
    }

    /** 删除一批已超过保留期且没有正在生成回复的会话。 */
    public int deleteExpiredBefore(Instant cutoff, int batchSize) {
        if (batchSize < 1) throw new IllegalArgumentException("清理批次必须为正数");
        List<String> ids = conversations.findExpiredIds(cutoff, PageRequest.of(0, batchSize));
        if (ids.isEmpty()) return 0;
        memory.deleteForConversations(ids);
        turns.deleteByConversationIds(ids);
        conversations.deleteAllByIdInBatch(ids);
        return ids.size();
    }

    public record StartedTurn(Long id, List<Message> history) {}

    public StartedTurn begin(Long userId, String id, Conversation.AppType appType, String message) {
        Conversation conversation = conversations.lockOwned(id, userId)
                .orElseThrow(() -> new NotFoundException("会话不存在"));
        if (conversation.getAppType() != appType) throw new NotFoundException("会话不存在");
        if (turns.existsByConversationIdAndStatus(id, ChatTurn.Status.STREAMING)) {
            throw new ConflictException("这个会话正在回复，请稍后再发送");
        }
        // 历史页面保留全部轮次；模型上下文由长期事实、滚动摘要和 token 预算内的最近完整轮次组成。
        List<Message> history = memory.buildContext(conversation, message);
        ChatTurn turn = new ChatTurn();
        turn.setConversation(conversation);
        turn.setUserContent(message);
        turns.save(turn);
        if ("新会话".equals(conversation.getTitle())) {
            String title = message.replaceAll("\\s+", " ").strip();
            conversation.setTitle(title.substring(0, Math.min(title.length(), 40)));
        }
        conversation.setUpdatedAt(Instant.now());
        memory.captureExplicitFacts(conversation, turn, message);
        return new StartedTurn(turn.getId(), history);
    }

    public void append(Long turnId, String chunk) {
        ChatTurn turn = turns.lockById(turnId).orElseThrow();
        if (turn.getStatus() == ChatTurn.Status.STREAMING) {
            turn.setAssistantContent(turn.getAssistantContent() + chunk);
        }
    }

    public void finish(Long turnId, ChatTurn.Status status) {
        ChatTurn turn = turns.lockById(turnId).orElseThrow();
        if (turn.getStatus() == ChatTurn.Status.STREAMING) {
            turn.setStatus(status);
            turn.getConversation().setUpdatedAt(Instant.now());
            if (status == ChatTurn.Status.COMPLETED) {
                memory.compactAfterCompletion(turn.getConversation());
            }
        }
    }

    // 单实例部署：重启时保留已落盘的片段，并解除上一进程留下的生成中状态。
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        turns.recoverInterrupted();
    }
}
