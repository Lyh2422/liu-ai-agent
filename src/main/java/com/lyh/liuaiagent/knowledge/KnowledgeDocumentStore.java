package com.lyh.liuaiagent.knowledge;

import com.lyh.liuaiagent.auth.exception.ConflictException;
import com.lyh.liuaiagent.auth.exception.NotFoundException;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class KnowledgeDocumentStore {
    private final KnowledgeDocumentRepository repository;
    private final ResourcePatternResolver resources;
    private final TransactionTemplate transactions;

    public KnowledgeDocumentStore(KnowledgeDocumentRepository repository, ResourcePatternResolver resources, PlatformTransactionManager manager) {
        this.repository = repository;
        this.resources = resources;
        this.transactions = new TransactionTemplate(manager);
    }

    @PostConstruct
    public void importDefaults() throws IOException {
        var defaults = resources.getResources("classpath:document/*.md");
        transactions.executeWithoutResult(status -> {
            for (var resource : defaults) {
                String filename = Objects.requireNonNull(resource.getFilename());
                String id = UUID.nameUUIDFromBytes(("builtin:" + filename).getBytes(StandardCharsets.UTF_8)).toString();
                // 已删除的默认文档保留墓碑，重启时不会重新导入。
                if (repository.existsById(id)) continue;
                try {
                    var document = new KnowledgeDocument();
                    document.setId(id);
                    document.setFilename(filename);
                    document.setTitle(filename.substring(0, filename.length() - 3));
                    document.setContent(KnowledgeValidation.content(resource.getContentAsString(StandardCharsets.UTF_8)));
                    document.setBuiltin(true);
                    repository.save(document);
                } catch (IOException error) { throw new IllegalStateException("默认知识文档导入失败", error); }
            }
        });
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> all() { return repository.findByDeletedFalseOrderByUpdatedAtDesc(); }

    @Transactional(readOnly = true)
    public KnowledgeDocument get(String id) {
        return repository.findByIdAndDeletedFalse(id).orElseThrow(() -> new NotFoundException("文档不存在或已删除"));
    }

    public KnowledgeDocument create(KnowledgeDocument document) { return repository.saveAndFlush(document); }

    public KnowledgeDocument update(String id, long version, String title, String content, Long userId) {
        var document = get(id);
        checkVersion(document, version);
        document.setTitle(title);
        document.setContent(content);
        document.setUpdatedBy(userId);
        document.setUpdatedAt(Instant.now());
        return repository.saveAndFlush(document);
    }

    public void delete(String id, long version) {
        var document = get(id);
        checkVersion(document, version);
        document.setDeleted(true);
        document.setUpdatedAt(Instant.now());
        repository.saveAndFlush(document);
    }

    public void checkVersion(KnowledgeDocument document, long version) {
        if (!Objects.equals(document.getVersion(), version)) throw new ConflictException("文档已被其他管理员修改，请重新加载后再操作");
    }
}
