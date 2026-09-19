package com.lyh.liuaiagent.auth.repository;

import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.auth.model.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    long countByRoleAndEnabledTrue(UserRole role);
    Page<UserAccount> findByUsernameContainingIgnoreCaseOrCollegeContainingIgnoreCase(
            String username, String college, Pageable pageable);
}
