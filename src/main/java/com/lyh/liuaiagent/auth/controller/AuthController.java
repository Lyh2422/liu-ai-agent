package com.lyh.liuaiagent.auth.controller;

import com.lyh.liuaiagent.auth.dto.*;
import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.auth.service.AuthService;
import com.lyh.liuaiagent.auth.service.AvatarStorageService;
import com.lyh.liuaiagent.auth.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final UserProfileService profileService;
    private final AvatarStorageService avatarStorageService;

    public AuthController(AuthService authService, UserProfileService profileService,
                          AvatarStorageService avatarStorageService) {
        this.authService = authService;
        this.profileService = profileService;
        this.avatarStorageService = avatarStorageService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return profileService.getSelf(currentUser(authentication));
    }

    @PutMapping("/me")
    public UserResponse updateProfile(Authentication authentication,
                                      @Valid @RequestBody ProfileUpdateRequest request) {
        return profileService.updateSelf(currentUser(authentication), request);
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadAvatar(Authentication authentication, @RequestPart("file") MultipartFile file)
            throws IOException {
        UserAccount user = currentUser(authentication);
        String avatarUrl = avatarStorageService.store(file);
        return profileService.updateSelf(user,
                new ProfileUpdateRequest(user.getGrade(), user.getCollege(), user.getSignature(), avatarUrl));
    }

    private UserAccount currentUser(Authentication authentication) {
        return (UserAccount) authentication.getPrincipal();
    }
}
