package com.lyh.liuaiagent.auth.service;

import com.lyh.liuaiagent.auth.dto.LoginRequest;
import com.lyh.liuaiagent.auth.dto.RegisterRequest;
import com.lyh.liuaiagent.auth.exception.ConflictException;
import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.auth.model.UserRole;
import com.lyh.liuaiagent.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private UserAccountRepository repository;
    private AuthService service;
    private BCryptPasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        repository = mock(UserAccountRepository.class);
        encoder = new BCryptPasswordEncoder();
        service = new AuthService(repository, encoder, mock(JwtService.class));
        when(repository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
    }

    @Test
    void registerCreatesOrdinaryUserWithHashedPassword() {
        when(repository.existsByUsernameIgnoreCase("alice")).thenReturn(false);

        var response = service.register(new RegisterRequest("alice", "password123", "2026", "计算机学院", "你好"));

        assertEquals("alice", response.username());
        assertEquals(UserRole.USER, response.role());
        assertFalse(response.enabled() == false);
        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).save(captor.capture());
        assertNotEquals("password123", captor.getValue().getPasswordHash());
        assertTrue(encoder.matches("password123", captor.getValue().getPasswordHash()));
    }

    @Test
    void duplicateUsernameIsRejected() {
        when(repository.existsByUsernameIgnoreCase("alice")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.register(new RegisterRequest("alice", "password123", null, null, null)));
        verify(repository, never()).save(any());
    }

    @Test
    void disabledOrWrongPasswordCannotLogin() {
        UserAccount user = new UserAccount();
        user.setUsername("alice");
        user.setPasswordHash(encoder.encode("password123"));
        user.setEnabled(false);
        when(repository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));

        assertThrows(BadCredentialsException.class,
                () -> service.login(new LoginRequest("alice", "password123")));
    }
}
