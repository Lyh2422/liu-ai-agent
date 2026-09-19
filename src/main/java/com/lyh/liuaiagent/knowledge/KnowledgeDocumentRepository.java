package com.lyh.liuaiagent.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {
    List<KnowledgeDocument> findByDeletedFalseOrderByUpdatedAtDesc();
    Optional<KnowledgeDocument> findByIdAndDeletedFalse(String id);
}
