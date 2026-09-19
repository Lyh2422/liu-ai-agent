package com.lyh.liuaiagent.conversation;

import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface ChatTurnRepository extends JpaRepository<ChatTurn, Long> {
    List<ChatTurn> findByConversationIdOrderByIdAsc(String conversationId);
    List<ChatTurn> findTop5ByConversationIdAndStatusOrderByIdDesc(String conversationId, ChatTurn.Status status);
    boolean existsByConversationIdAndStatus(String conversationId, ChatTurn.Status status);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ChatTurn t where t.id = :id")
    java.util.Optional<ChatTurn> lockById(Long id);
    @Modifying
    @Query("update ChatTurn t set t.status = com.lyh.liuaiagent.conversation.ChatTurn.Status.INTERRUPTED where t.status = com.lyh.liuaiagent.conversation.ChatTurn.Status.STREAMING")
    int recoverInterrupted();
}
