package com.lyh.liuaiagent.auth.service;

import com.lyh.liuaiagent.auth.dto.AdminUserUpdateRequest;
import com.lyh.liuaiagent.auth.dto.ProfileUpdateRequest;
import com.lyh.liuaiagent.auth.dto.UserResponse;
import com.lyh.liuaiagent.auth.exception.ConflictException;
import com.lyh.liuaiagent.auth.exception.NotFoundException;
import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.auth.model.UserRole;
import com.lyh.liuaiagent.auth.repository.UserAccountRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {
    private final UserAccountRepository repository;

    public UserProfileService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserResponse updateSelf(UserAccount user, ProfileUpdateRequest request) {
        applyProfile(user, request.grade(), request.college(), request.signature(), request.avatarUrl(), false);
        return UserResponse.from(repository.save(user));
    }

    @Transactional
    public UserResponse updateByAdmin(Long id, AdminUserUpdateRequest request, Long operatorId) {
        UserAccount target = repository.findById(id).orElseThrow(() -> new NotFoundException("用户不存在"));
        UserRole nextRole = request.role() == null ? target.getRole() : request.role();
        boolean nextEnabled = request.enabled() == null ? target.isEnabled() : request.enabled();
        if (target.getRole() == UserRole.ADMIN && nextRole != UserRole.ADMIN
                || target.getRole() == UserRole.ADMIN && !nextEnabled) {
            if (repository.countByRoleAndEnabledTrue(UserRole.ADMIN) <= 1) {
                throw new ConflictException("系统至少需要保留一个启用中的管理员");
            }
        }
        if (target.getId().equals(operatorId) && nextEnabled == false && nextRole != UserRole.ADMIN) {
            throw new ConflictException("不能同时停用并降级当前管理员");
        }
        applyProfile(target, request.grade(), request.college(), request.signature(), request.avatarUrl(), false);
        target.setRole(nextRole);
        target.setEnabled(nextEnabled);
        return UserResponse.from(repository.save(target));
    }

    public Page<UserResponse> list(String keyword, Pageable pageable) {
        Page<UserAccount> users = keyword == null || keyword.isBlank()
                ? repository.findAll(pageable)
                : repository.findByUsernameContainingIgnoreCaseOrCollegeContainingIgnoreCase(
                        keyword.trim(), keyword.trim(), pageable);
        return users.map(UserResponse::from);
    }

    private void applyProfile(UserAccount user, String grade, String college, String signature, String avatarUrl,
                              boolean replaceNulls) {
        if (replaceNulls || grade != null) user.setGrade(trimToNull(grade));
        if (replaceNulls || college != null) user.setCollege(trimToNull(college));
        if (replaceNulls || signature != null) user.setSignature(trimToNull(signature));
        if (replaceNulls || avatarUrl != null) user.setAvatarUrl(trimToNull(avatarUrl));
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
