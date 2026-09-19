package com.lyh.liuaiagent.knowledge;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_documents")
@Getter
@Setter
public class KnowledgeDocument {
    @Id private String id = UUID.randomUUID().toString();
    @Version private Long version;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, length = 255) private String filename;
    @Lob @Column(nullable = false) private String content;
    @Column(nullable = false) private boolean builtin;
    @Column(nullable = false) private boolean deleted;
    private Long updatedBy;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = createdAt;
}
