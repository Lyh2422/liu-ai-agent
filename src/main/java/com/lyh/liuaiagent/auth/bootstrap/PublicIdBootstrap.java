package com.lyh.liuaiagent.auth.bootstrap;

import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.auth.repository.UserAccountRepository;
import jakarta.transaction.Transactional;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Assigns public IDs to accounts created before the social features were introduced. */
@Component
@Order(1)
public class PublicIdBootstrap implements ApplicationRunner {
    private final UserAccountRepository repository;

    public PublicIdBootstrap(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (UserAccount user : repository.findByPublicIdIsNull()) {
            String publicId;
            do {
                publicId = UserAccount.newPublicId();
            } while (repository.existsByPublicIdIgnoreCase(publicId));
            user.setPublicId(publicId);
        }
    }
}
