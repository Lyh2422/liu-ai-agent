package com.lyh.liuaiagent.conversation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "chat_turns", indexes = @Index(name = "idx_turn_conversation", columnList = "conversation_id,id"))
@Getter
@Setter
public class ChatTurn {
    public enum Status { STREAMING, COMPLETED, FAILED, INTERRUPTED }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String userContent;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String assistantContent = "";
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.STREAMING;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
