package com.lyh.liuaiagent.auth.dto;

import com.lyh.liuaiagent.auth.model.UserRole;
import jakarta.validation.constraints.Size;

public record AdminUserUpdateRequest(
        @Size(max = 32) String grade,
        @Size(max = 80) String college,
        @Size(max = 300) String signature,
        @Size(max = 500) String avatarUrl,
        UserRole role,
        Boolean enabled
) {}
