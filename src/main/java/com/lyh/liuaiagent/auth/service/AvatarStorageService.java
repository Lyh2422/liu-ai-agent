package com.lyh.liuaiagent.auth.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface AvatarStorageService {
    String store(MultipartFile file) throws IOException;
}
