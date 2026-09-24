package com.lyh.liuaiagent.conversation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** 用户明确表达的结构化事实；不保存模型推测。 */
@Entity
@Table(name = "user_memory_facts", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_memory_fact_key", columnNames = {"user_id", "fact_key"}), indexes = {
        @Index(name = "idx_user_memory_fact_user_updated", columnList = "user_id,updated_at"),
        @Index(name = "idx_user_memory_fact_source", columnList = "source_conversation_id")
})
@Getter
@Setter
public class UserMemoryFact {
    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "fact_type", nullable = false, length = 32)
    private String factType;

    @Column(name = "fact_key", nullable = false, length = 96)
    private String factKey;

    @Column(name = "fact_value", nullable = false, length = 500)
    private String factValue;

    @Column(name = "source_conversation_id", nullable = false, length = 36)
    private String sourceConversationId;

    @Column(name = "source_turn_id", nullable = false)
    private Long sourceTurnId;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = createdAt;
}
