package com.lyh.liuaiagent.social.repository;

import com.lyh.liuaiagent.social.model.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, String> {
    Optional<Friendship> findByLowerUserIdAndHigherUserId(Long lowerUserId, Long higherUserId);

    @Query("""
            select friendship from Friendship friendship
            where friendship.lowerUserId = :userId or friendship.higherUserId = :userId
            order by friendship.createdAt desc
            """)
    List<Friendship> findAllForUser(@Param("userId") Long userId);
}
