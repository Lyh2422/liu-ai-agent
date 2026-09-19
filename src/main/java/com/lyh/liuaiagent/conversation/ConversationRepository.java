package com.lyh.liuaiagent.conversation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, String> {
    List<Conversation> findByUserIdAndAppTypeOrderByUpdatedAtDesc(Long userId, Conversation.AppType appType);
    Optional<Conversation> findByIdAndUserId(String id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id and c.userId = :userId")
    Optional<Conversation> lockOwned(String id, Long userId);
}
