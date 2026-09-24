package com.lyh.liuaiagent.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ConversationMemoryRepository extends JpaRepository<ConversationMemory, String> {
    @Modifying
    @Query("delete from ConversationMemory m where m.conversationId in :conversationIds")
    int deleteByConversationIds(List<String> conversationIds);
}
