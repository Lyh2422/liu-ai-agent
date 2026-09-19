package com.lyh.liuaiagent.knowledge;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.ArrayList;

@Service
@lombok.extern.slf4j.Slf4j
public class KnowledgeManagementService {
    private final KnowledgeDocumentStore store;
    private final KnowledgeIndex index;
    public KnowledgeManagementService(KnowledgeDocumentStore store, KnowledgeIndex index) {
        this.store = store;
        this.index = index;
    }
    // 单实例内串行发布管理操作；数据库版本号另外保护跨标签页的过期编辑。
    public synchronized KnowledgeDocument upload(MultipartFile file, Long userId) throws IOException {
        var document = new KnowledgeDocument();
        document.setFilename(KnowledgeValidation.filename(file.getOriginalFilename()));
        String stem = document.getFilename().substring(0, document.getFilename().lastIndexOf('.'));
        document.setTitle(KnowledgeValidation.title(stem.substring(0, Math.min(stem.length(), 120))));
        document.setContent(KnowledgeValidation.read(file));
        document.setUpdatedBy(userId);
        var documents = new ArrayList<>(store.all());
        documents.add(document);
        var prepared = prepare(documents);
        var saved = store.create(document);
        index.publish(prepared);
        return saved;
    }

    public synchronized KnowledgeDocument update(String id, long version, String title, String content, Long userId) {
        title = KnowledgeValidation.title(title);
        content = KnowledgeValidation.content(content);
        var document = store.get(id);
        store.checkVersion(document, version);
        document.setTitle(title);
        document.setContent(content);
        var documents = new ArrayList<>(store.all());
        documents.removeIf(item -> item.getId().equals(id));
        documents.add(document);
        var prepared = prepare(documents);
        var saved = store.update(id, version, title, content, userId);
        index.publish(prepared);
        return saved;
    }

    public synchronized void delete(String id, long version) {
        var document = store.get(id);
        store.checkVersion(document, version);
        var documents = new ArrayList<>(store.all());
        documents.removeIf(item -> item.getId().equals(id));
        var prepared = prepare(documents);
        store.delete(id, version);
        index.publish(prepared);
    }

    private KnowledgeIndex.Snapshot prepare(java.util.List<KnowledgeDocument> documents) {
        try { return index.prepare(KnowledgeDocuments.chunks(documents)); }
        catch (RuntimeException error) {
            log.warn("知识索引更新失败，保留原文档与索引", error);
            throw new IndexUpdateException("知识索引更新失败，本次修改未保存，请稍后重试", error);
        }
    }
    public static class IndexUpdateException extends RuntimeException {
        public IndexUpdateException(String message, Throwable cause) { super(message, cause); }
    }
}
