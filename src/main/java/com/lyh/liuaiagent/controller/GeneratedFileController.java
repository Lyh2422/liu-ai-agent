package com.lyh.liuaiagent.controller;

import com.lyh.liuaiagent.auth.exception.NotFoundException;
import com.lyh.liuaiagent.generated.GeneratedFileStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/ai/generated-files")
public class GeneratedFileController {
    private static final MediaType MARKDOWN = new MediaType("text", "markdown", StandardCharsets.UTF_8);
    private final GeneratedFileStore generatedFiles;

    public GeneratedFileController(GeneratedFileStore generatedFiles) {
        this.generatedFiles = generatedFiles;
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileSystemResource> download(@PathVariable String id) {
        GeneratedFileStore.StoredGeneratedFile file = generatedFiles.findMarkdown(id)
                .orElseThrow(() -> new NotFoundException("生成文件不存在或已失效"));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MARKDOWN)
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new FileSystemResource(file.path()));
    }
}
