package com.lyh.liuaiagent.social.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "social_chat_rooms", indexes = @Index(name = "idx_social_room_updated", columnList = "updated_at"))
@Getter
@Setter
@NoArgsConstructor
public class SocialChatRoom {
    public enum RoomType { DIRECT, GROUP }

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RoomType type;

    @Column(length = 80)
    private String name;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "direct_key", unique = true, length = 48)
    private String directKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
}
