package com.lyh.liuaiagent.auth.service;

import com.lyh.liuaiagent.auth.dto.*;
import com.lyh.liuaiagent.auth.exception.ConflictException;
import com.lyh.liuaiagent.auth.exception.NotFoundException;
import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.auth.model.UserRole;
import com.lyh.liuaiagent.auth.repository.UserAccountRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserAccountRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (repository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("用户名已存在");
        }
        UserAccount user = new UserAccount();
        user.setPublicId(nextPublicId());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        user.setGrade(trimToNull(request.grade()));
        user.setCollege(trimToNull(request.college()));
        user.setSignature(trimToNull(request.signature()));
        return UserResponse.from(repository.save(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserAccount user = repository.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (user.getPublicId() == null || user.getPublicId().isBlank()) {
            user.setPublicId(nextPublicId());
            user = repository.save(user);
        }
        return new AuthResponse(jwtService.createToken(user), UserResponse.from(user));
    }

    public UserAccount requireUser(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("用户不存在"));
    }

    @Transactional
    public UserAccount bootstrapAdmin(String username, String password) {
        return repository.findByUsernameIgnoreCase(username).orElseGet(() -> {
            UserAccount admin = new UserAccount();
            admin.setPublicId(nextPublicId());
            admin.setUsername(username);
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setRole(UserRole.ADMIN);
            admin.setEnabled(true);
            return repository.save(admin);
        });
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nextPublicId() {
        String publicId;
        do {
            publicId = UserAccount.newPublicId();
        } while (repository.existsByPublicIdIgnoreCase(publicId));
        return publicId;
    }
}
