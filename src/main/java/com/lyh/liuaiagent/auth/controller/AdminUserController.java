package com.lyh.liuaiagent.auth.controller;

import com.lyh.liuaiagent.auth.dto.AdminUserUpdateRequest;
import com.lyh.liuaiagent.auth.dto.UserResponse;
import com.lyh.liuaiagent.auth.dto.UserPageResponse;
import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.auth.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {
    private final UserProfileService profileService;

    public AdminUserController(UserProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public UserPageResponse list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<UserResponse> result = profileService.list(keyword,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new UserPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id,
                               @Valid @RequestBody AdminUserUpdateRequest request,
                               Authentication authentication) {
        UserAccount operator = (UserAccount) authentication.getPrincipal();
        return profileService.updateByAdmin(id, request, operator.getId());
    }
}
