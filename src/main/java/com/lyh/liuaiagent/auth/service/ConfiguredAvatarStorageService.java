package com.lyh.liuaiagent.auth.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import com.lyh.liuaiagent.auth.config.AuthProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@Primary
public class ConfiguredAvatarStorageService implements AvatarStorageService {
    private final AuthProperties properties;
    private final LocalAvatarStorageService localStorage;

    public ConfiguredAvatarStorageService(AuthProperties properties, LocalAvatarStorageService localStorage) {
        this.properties = properties;
        this.localStorage = localStorage;
    }

    @Override
    public String store(MultipartFile file) throws IOException {
        localStorage.validate(file);
        if (!properties.getCos().isConfigured()) {
            return localStorage.storeValidated(file);
        }
        String suffix = suffix(file.getOriginalFilename());
        String key = "avatars/" + UUID.randomUUID() + suffix;
        COSCredentials credentials = new BasicCOSCredentials(
                properties.getCos().getSecretId(), properties.getCos().getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(properties.getCos().getRegion()));
        COSClient client = new COSClient(credentials, clientConfig);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());
            client.putObject(new PutObjectRequest(
                    properties.getCos().getBucket(), key, file.getInputStream(), metadata));
        } finally {
            client.shutdown();
        }
        String baseUrl = properties.getCos().getPublicBaseUrl();
        return baseUrl == null || baseUrl.isBlank()
                ? "https://" + properties.getCos().getBucket() + ".cos." + properties.getCos().getRegion()
                    + ".myqcloud.com/" + key
                : baseUrl.replaceAll("/$", "") + "/" + key;
    }

    private String suffix(String originalName) {
        if (originalName == null || !originalName.contains(".")) return ".jpg";
        return originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
    }
}
