package com.lyh.liuaiagent.social.repository;

import com.lyh.liuaiagent.social.model.SocialChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialChatRoomRepository extends JpaRepository<SocialChatRoom, String> {
    Optional<SocialChatRoom> findByDirectKey(String directKey);
}
