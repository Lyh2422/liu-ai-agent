package com.lyh.liuaiagent.conversation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_conversations", indexes = @Index(name = "idx_conversation_owner_app", columnList = "userId,appType,updatedAt"))
@Getter
@Setter
public class Conversation {
    public enum AppType { LOVE, MANUS }
    @Id
    private String id = UUID.randomUUID().toString();
    @Column(nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AppType appType;
    @Column(nullable = false, length = 80)
    private String title = "新会话";
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = createdAt;
}
