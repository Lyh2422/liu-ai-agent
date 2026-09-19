package com.lyh.liuaiagent.auth.service;

import com.lyh.liuaiagent.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfiguredAvatarStorageServiceTest {
    @Test
    void rejectsInvalidImageBeforeCosUpload() {
        AuthProperties properties = new AuthProperties();
        properties.getCos().setEnabled(true);
        properties.getCos().setSecretId("secret-id");
        properties.getCos().setSecretKey("secret-key");
        properties.getCos().setRegion("ap-shanghai");
        properties.getCos().setBucket("bucket-123");

        LocalAvatarStorageService localStorage = new LocalAvatarStorageService(properties);
        ConfiguredAvatarStorageService storage = new ConfiguredAvatarStorageService(properties, localStorage);
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", "not-an-image".getBytes());

        assertThrows(IOException.class, () -> storage.store(file));
    }
}
