package com.lyh.liuaiagent.social.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "friendships", uniqueConstraints = @UniqueConstraint(
        name = "uk_friendship_pair", columnNames = {"lower_user_id", "higher_user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Friendship {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "lower_user_id", nullable = false)
    private Long lowerUserId;

    @Column(name = "higher_user_id", nullable = false)
    private Long higherUserId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }
}
