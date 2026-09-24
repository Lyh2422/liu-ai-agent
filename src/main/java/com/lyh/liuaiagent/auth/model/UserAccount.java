package com.lyh.liuaiagent.auth.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.security.SecureRandom;

@Entity
@Table(name = "user_accounts", uniqueConstraints = @UniqueConstraint(name = "uk_user_account_username", columnNames = "username"))
@Getter
@Setter
@NoArgsConstructor
public class UserAccount {
    private static final String PUBLIC_ID_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom PUBLIC_ID_RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", unique = true, length = 11)
    private String publicId;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 32)
    private String grade;

    @Column(length = 80)
    private String college;

    @Column(length = 300)
    private String signature;

    @Column(length = 500)
    private String avatarUrl;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicId == null || publicId.isBlank()) {
            publicId = newPublicId();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static String newPublicId() {
        StringBuilder value = new StringBuilder("U");
        for (int index = 0; index < 10; index++) {
            value.append(PUBLIC_ID_ALPHABET.charAt(PUBLIC_ID_RANDOM.nextInt(PUBLIC_ID_ALPHABET.length())));
        }
        return value.toString();
    }
}
