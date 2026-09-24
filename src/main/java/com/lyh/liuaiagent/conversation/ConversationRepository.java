package com.lyh.liuaiagent.conversation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, String> {
    List<Conversation> findByUserIdAndAppTypeOrderByUpdatedAtDesc(Long userId, Conversation.AppType appType);
    Optional<Conversation> findByIdAndUserId(String id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id and c.userId = :userId")
    Optional<Conversation> lockOwned(String id, Long userId);
    @Query("""
            select c.id from Conversation c
            where c.updatedAt < :cutoff
              and not exists (
                select t.id from ChatTurn t
                where t.conversation = c
                  and t.status = com.lyh.liuaiagent.conversation.ChatTurn.Status.STREAMING
              )
            order by c.updatedAt asc
            """)
    List<String> findExpiredIds(Instant cutoff, Pageable pageable);
}
