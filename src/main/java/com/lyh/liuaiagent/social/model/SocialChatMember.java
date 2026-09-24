package com.lyh.liuaiagent.social.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "social_chat_members", uniqueConstraints = @UniqueConstraint(
        name = "uk_social_chat_member", columnNames = {"room_id", "user_id"}), indexes = {
        @Index(name = "idx_social_member_user", columnList = "user_id"),
        @Index(name = "idx_social_member_room", columnList = "room_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SocialChatMember {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "room_id", nullable = false, length = 36)
    private String roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (joinedAt == null) joinedAt = Instant.now();
        if (lastReadAt == null) lastReadAt = joinedAt;
    }
}
