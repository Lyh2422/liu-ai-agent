package com.lyh.liuaiagent.auth.service;

import com.lyh.liuaiagent.auth.config.AuthProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalAvatarStorageService implements AvatarStorageService {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private final AuthProperties properties;

    public LocalAvatarStorageService(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public String store(MultipartFile file) throws IOException {
        validate(file);
        return storeValidated(file);
    }

    String storeValidated(MultipartFile file) throws IOException {
        Path directory = Path.of(properties.getUpload().getDir()).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        String extension = extensionFor(file.getContentType());
        String fileName = UUID.randomUUID() + extension;
        Path target = directory.resolve(fileName).normalize();
        if (!target.getParent().equals(directory)) {
            throw new IOException("非法文件路径");
        }
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return "/uploads/" + fileName;
    }

    public void validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("头像文件不能为空");
        }
        if (file.getSize() > properties.getUpload().getMaxSizeBytes()) {
            throw new IOException("头像文件不能超过 " + properties.getUpload().getMaxSizeBytes() / 1024 / 1024 + "MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IOException("仅支持 JPG、PNG、GIF、WEBP 图片");
        }
        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) throw new IOException("文件不是有效图片");
        }
    }

    private String extensionFor(String contentType) {
        return switch (StringUtils.hasText(contentType) ? contentType.toLowerCase(Locale.ROOT) : "") {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
