package com.lyh.liuaiagent.conversation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** 会话级滚动摘要。原始消息仍保存在 chat_turns，这里只保存有界的派生记忆。 */
@Entity
@Table(name = "conversation_memories")
@Getter
@Setter
public class ConversationMemory {
    @Id
    @Column(name = "conversation_id", length = 36)
    private String conversationId;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary = "";

    @Column(name = "summarized_through_turn_id", nullable = false)
    private Long summarizedThroughTurnId = 0L;

    @Version
    private Long version;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
