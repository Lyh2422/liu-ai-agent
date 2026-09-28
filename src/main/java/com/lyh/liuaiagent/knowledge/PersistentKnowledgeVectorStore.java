package com.lyh.liuaiagent.knowledge;

import org.springframework.ai.vectorstore.VectorStore;

import java.util.Collection;
import java.util.Set;

/** PGVector 持久化索引需要的少量维护能力，业务检索仍只依赖 VectorStore。 */
public interface PersistentKnowledgeVectorStore extends VectorStore {
    Set<String> existingIds(Collection<String> ids);
    void deleteVersion(String version);
    void deleteVersionsExcept(String activeVersion);
}
