package com.lyh.liuaiagent.conversation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserMemoryFactRepository extends JpaRepository<UserMemoryFact, String> {
    Optional<UserMemoryFact> findByUserIdAndFactKey(Long userId, String factKey);

    @Query("select f from UserMemoryFact f where f.userId = :userId order by f.updatedAt desc")
    List<UserMemoryFact> findRecentByUserId(Long userId, Pageable pageable);

    Optional<UserMemoryFact> findByIdAndUserId(String id, Long userId);

    @Modifying
    int deleteByIdAndUserId(String id, Long userId);

    @Modifying
    @Query("delete from UserMemoryFact f where f.sourceConversationId in :conversationIds")
    int deleteBySourceConversationIds(List<String> conversationIds);
}
