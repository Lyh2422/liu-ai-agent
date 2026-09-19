package com.lyh.liuaiagent.conversation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
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
    @Lob
    @Column(nullable = false)
    private String userContent;
    @Lob
    @Column(nullable = false)
    private String assistantContent = "";
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.STREAMING;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
