package com.lyh.liuaiagent.conversation;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ChatTurnRepository extends JpaRepository<ChatTurn, Long> {
    List<ChatTurn> findByConversationIdOrderByIdAsc(String conversationId);
    @Query("""
            select t from ChatTurn t
            where t.conversation.id = :conversationId
              and t.status = com.lyh.liuaiagent.conversation.ChatTurn.Status.COMPLETED
              and t.id > :afterTurnId
            order by t.id desc
            """)
    List<ChatTurn> findRecentCompletedAfter(String conversationId, Long afterTurnId, Pageable pageable);
    @Query("""
            select t from ChatTurn t
            where t.conversation.id = :conversationId
              and t.status = com.lyh.liuaiagent.conversation.ChatTurn.Status.COMPLETED
              and t.id > :afterTurnId
            order by t.id asc
            """)
    List<ChatTurn> findCompletedAfterOrderByIdAsc(String conversationId, Long afterTurnId);
    boolean existsByConversationIdAndStatus(String conversationId, ChatTurn.Status status);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ChatTurn t where t.id = :id")
    java.util.Optional<ChatTurn> lockById(Long id);
    @Modifying
    @Query("update ChatTurn t set t.status = com.lyh.liuaiagent.conversation.ChatTurn.Status.INTERRUPTED where t.status = com.lyh.liuaiagent.conversation.ChatTurn.Status.STREAMING")
    int recoverInterrupted();
    @Modifying
    @Query("delete from ChatTurn t where t.conversation.id = :conversationId")
    int deleteByConversationId(String conversationId);
    @Modifying
    @Query("delete from ChatTurn t where t.conversation.id in :conversationIds")
    int deleteByConversationIds(List<String> conversationIds);
}
