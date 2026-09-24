package com.lyh.liuaiagent.social.repository;

import com.lyh.liuaiagent.social.model.SocialChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SocialChatMessageRepository extends JpaRepository<SocialChatMessage, String> {
    List<SocialChatMessage> findTop100ByRoomIdOrderByCreatedAtDesc(String roomId);
    Optional<SocialChatMessage> findTopByRoomIdOrderByCreatedAtDesc(String roomId);
    long countByRoomIdAndSenderIdNotAndCreatedAtAfter(String roomId, Long senderId, Instant createdAt);
}
