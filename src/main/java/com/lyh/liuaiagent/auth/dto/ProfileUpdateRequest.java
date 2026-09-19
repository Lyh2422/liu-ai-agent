package com.lyh.liuaiagent.auth.dto;

import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @Size(max = 32) String grade,
        @Size(max = 80) String college,
        @Size(max = 300) String signature,
        @Size(max = 500) String avatarUrl
) {}
