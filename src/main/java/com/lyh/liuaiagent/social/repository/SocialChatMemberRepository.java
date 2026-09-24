package com.lyh.liuaiagent.social.repository;

import com.lyh.liuaiagent.social.model.SocialChatMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocialChatMemberRepository extends JpaRepository<SocialChatMember, String> {
    boolean existsByRoomIdAndUserId(String roomId, Long userId);
    Optional<SocialChatMember> findByRoomIdAndUserId(String roomId, Long userId);
    List<SocialChatMember> findByUserId(Long userId);
    List<SocialChatMember> findByRoomIdOrderByJoinedAtAsc(String roomId);
    long countByRoomId(String roomId);
}
