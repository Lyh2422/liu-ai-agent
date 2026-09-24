package com.lyh.liuaiagent.knowledge;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_documents")
@Getter
@Setter
public class KnowledgeDocument {
    @Id @Column(length = 36) private String id = UUID.randomUUID().toString();
    @Version private Long version;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, length = 255) private String filename;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(nullable = false) private boolean builtin;
    @Column(nullable = false) private boolean deleted;
    private Long updatedBy;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = createdAt;
}
