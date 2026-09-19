package com.lyh.liuaiagent.conversation;

import com.lyh.liuaiagent.auth.exception.ConflictException;
import com.lyh.liuaiagent.auth.exception.NotFoundException;
import org.springframework.ai.chat.messages.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class ConversationStore {
    private final ConversationRepository conversations;
    private final ChatTurnRepository turns;
    public ConversationStore(ConversationRepository conversations, ChatTurnRepository turns) {
        this.conversations = conversations;
        this.turns = turns;
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

    public record StartedTurn(Long id, List<Message> history) {}

    public StartedTurn begin(Long userId, String id, Conversation.AppType appType, String message) {
        Conversation conversation = conversations.lockOwned(id, userId)
                .orElseThrow(() -> new NotFoundException("会话不存在"));
        if (conversation.getAppType() != appType) throw new NotFoundException("会话不存在");
        if (turns.existsByConversationIdAndStatus(id, ChatTurn.Status.STREAMING)) {
            throw new ConflictException("这个会话正在回复，请稍后再发送");
        }
        // 只将完整的最近五轮加入模型上下文；历史页面保留所有轮次，包括中断的内容。
        List<ChatTurn> recent = new ArrayList<>(turns.findTop5ByConversationIdAndStatusOrderByIdDesc(id, ChatTurn.Status.COMPLETED));
        Collections.reverse(recent);
        List<Message> history = new ArrayList<>();
        for (ChatTurn turn : recent) {
            history.add(new UserMessage(turn.getUserContent()));
            history.add(new AssistantMessage(turn.getAssistantContent()));
        }
        ChatTurn turn = new ChatTurn();
        turn.setConversation(conversation);
        turn.setUserContent(message);
        turns.save(turn);
        if ("新会话".equals(conversation.getTitle())) {
            String title = message.replaceAll("\\s+", " ").strip();
            conversation.setTitle(title.substring(0, Math.min(title.length(), 40)));
        }
        conversation.setUpdatedAt(Instant.now());
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
        }
    }

    // 单实例部署：重启时保留已落盘的片段，并解除上一进程留下的生成中状态。
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        turns.recoverInterrupted();
    }
}
