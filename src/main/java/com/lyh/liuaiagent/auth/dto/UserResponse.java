package com.lyh.liuaiagent.auth.dto;

import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.auth.model.UserRole;

import java.time.Instant;

public record UserResponse(
        Long id,
        String publicId,
        String username,
        UserRole role,
        boolean enabled,
        String grade,
        String college,
        String signature,
        String avatarUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getPublicId(), user.getUsername(), user.getRole(), user.isEnabled(),
                user.getGrade(), user.getCollege(), user.getSignature(), user.getAvatarUrl(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
