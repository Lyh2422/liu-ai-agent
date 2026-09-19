package com.lyh.liuaiagent.auth.dto;

import java.util.List;

public record UserPageResponse(List<UserResponse> content, int page, int size, long totalElements, int totalPages) {}
