package com.lyh.liuaiagent.rag;

import com.lyh.liuaiagent.knowledge.KnowledgeDocumentStore;
import com.lyh.liuaiagent.knowledge.KnowledgeDocuments;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class LoveAppDocumentLoader {
    private final KnowledgeDocumentStore store;
    public LoveAppDocumentLoader(KnowledgeDocumentStore store) { this.store = store; }
    public List<Document> loadMarkdowns() { return KnowledgeDocuments.chunks(store.all()); }
}
