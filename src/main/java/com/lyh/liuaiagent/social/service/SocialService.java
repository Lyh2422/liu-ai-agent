package com.lyh.liuaiagent.social.service;

import com.lyh.liuaiagent.auth.exception.ConflictException;
import com.lyh.liuaiagent.auth.exception.NotFoundException;
import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.auth.repository.UserAccountRepository;
import com.lyh.liuaiagent.social.dto.SocialDtos.*;
import com.lyh.liuaiagent.social.model.Friendship;
import com.lyh.liuaiagent.social.model.SocialChatMember;
import com.lyh.liuaiagent.social.model.SocialChatMessage;
import com.lyh.liuaiagent.social.model.SocialChatRoom;
import com.lyh.liuaiagent.social.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class SocialService {
    private final UserAccountRepository users;
    private final FriendshipRepository friendships;
    private final SocialChatRoomRepository rooms;
    private final SocialChatMemberRepository members;
    private final SocialChatMessageRepository messages;

    public SocialService(UserAccountRepository users, FriendshipRepository friendships,
                         SocialChatRoomRepository rooms, SocialChatMemberRepository members,
                         SocialChatMessageRepository messages) {
        this.users = users;
        this.friendships = friendships;
        this.rooms = rooms;
        this.members = members;
        this.messages = messages;
    }

    @Transactional
    public PublicUser addFriend(UserAccount current, String publicId) {
        UserAccount target = requirePublicUser(publicId);
        if (target.getId().equals(current.getId())) {
            throw new ConflictException("不能添加自己为好友");
        }
        long lower = Math.min(current.getId(), target.getId());
        long higher = Math.max(current.getId(), target.getId());
        if (friendships.findByLowerUserIdAndHigherUserId(lower, higher).isPresent()) {
            throw new ConflictException("你们已经是好友了");
        }
        Friendship friendship = new Friendship();
        friendship.setLowerUserId(lower);
        friendship.setHigherUserId(higher);
        friendship.setCreatedBy(current.getId());
        friendships.save(friendship);
        return PublicUser.from(target);
    }

    public List<PublicUser> listFriends(UserAccount current) {
        return friendships.findAllForUser(current.getId())
                .stream()
                .map(friendship -> friendship.getLowerUserId().equals(current.getId())
                        ? friendship.getHigherUserId() : friendship.getLowerUserId())
                .map(this::requireUser)
                .filter(UserAccount::isEnabled)
                .map(PublicUser::from)
                .toList();
    }

    @Transactional
    public void removeFriend(UserAccount current, String publicId) {
        UserAccount target = requirePublicUser(publicId);
        long lower = Math.min(current.getId(), target.getId());
        long higher = Math.max(current.getId(), target.getId());
        Friendship friendship = friendships.findByLowerUserIdAndHigherUserId(lower, higher)
                .orElseThrow(() -> new NotFoundException("好友关系不存在"));
        friendships.delete(friendship);
    }

    @Transactional
    public RoomResponse openDirect(UserAccount current, String publicId) {
        UserAccount target = requirePublicUser(publicId);
        if (target.getId().equals(current.getId())) {
            throw new ConflictException("不能和自己创建单聊");
        }
        String key = directKey(current.getId(), target.getId());
        SocialChatRoom room = rooms.findByDirectKey(key).orElseGet(() -> {
            SocialChatRoom created = new SocialChatRoom();
            created.setType(SocialChatRoom.RoomType.DIRECT);
            created.setDirectKey(key);
            created.setOwnerId(current.getId());
            rooms.save(created);
            addMember(created.getId(), current.getId());
            addMember(created.getId(), target.getId());
            return created;
        });
        return roomResponse(room, current.getId());
    }

    @Transactional
    public RoomResponse createGroup(UserAccount current, CreateGroupRequest request) {
        SocialChatRoom room = new SocialChatRoom();
        room.setType(SocialChatRoom.RoomType.GROUP);
        room.setName(request.name().trim());
        room.setOwnerId(current.getId());
        rooms.save(room);
        addMember(room.getId(), current.getId());

        Set<Long> invited = new LinkedHashSet<>();
        if (request.memberPublicIds() != null) {
            for (String publicId : request.memberPublicIds()) {
                if (publicId == null || publicId.isBlank()) continue;
                UserAccount target = requirePublicUser(publicId);
                if (!target.getId().equals(current.getId())) invited.add(target.getId());
            }
        }
        invited.forEach(userId -> addMember(room.getId(), userId));
        return roomResponse(room, current.getId());
    }

    @Transactional
    public RoomResponse inviteMember(UserAccount current, String roomId, String publicId) {
        SocialChatRoom room = requireRoomForMember(current, roomId);
        if (room.getType() != SocialChatRoom.RoomType.GROUP) {
            throw new ConflictException("单聊不能邀请新成员");
        }
        if (!current.getId().equals(room.getOwnerId())) {
            throw new AccessDeniedException("只有群主可以邀请成员");
        }
        UserAccount target = requirePublicUser(publicId);
        if (members.existsByRoomIdAndUserId(roomId, target.getId())) {
            throw new ConflictException("该用户已经在群聊中");
        }
        addMember(roomId, target.getId());
        room.setUpdatedAt(Instant.now());
        rooms.save(room);
        return roomResponse(room, current.getId());
    }

    public List<RoomResponse> listRooms(UserAccount current) {
        List<SocialChatRoom> joinedRooms = members.findByUserId(current.getId()).stream()
                .map(SocialChatMember::getRoomId)
                .map(rooms::findById)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(SocialChatRoom::getUpdatedAt).reversed())
                .toList();
        return joinedRooms.stream().map(room -> roomResponse(room, current.getId())).toList();
    }

    public RoomResponse getRoom(UserAccount current, String roomId) {
        return roomResponse(requireRoomForMember(current, roomId), current.getId());
    }

    public UnreadCountResponse unreadCount(UserAccount current) {
        long unreadCount = members.findByUserId(current.getId()).stream()
                .mapToLong(member -> unreadCount(member, current.getId()))
                .sum();
        return new UnreadCountResponse(unreadCount);
    }

    @Transactional
    public List<MessageResponse> listMessages(UserAccount current, String roomId) {
        requireRoomForMember(current, roomId);
        SocialChatMember member = requireMember(roomId, current.getId());
        List<SocialChatMessage> latest = new ArrayList<>(messages.findTop100ByRoomIdOrderByCreatedAtDesc(roomId));
        Collections.reverse(latest);
        if (!latest.isEmpty()) {
            Instant latestMessageAt = latest.getLast().getCreatedAt();
            if (member.getLastReadAt() == null || member.getLastReadAt().isBefore(latestMessageAt)) {
                member.setLastReadAt(latestMessageAt);
                members.save(member);
            }
        }
        return latest.stream().map(this::messageResponse).toList();
    }

    @Transactional
    public MessageResponse sendMessage(UserAccount current, String roomId, String content) {
        SocialChatRoom room = requireRoomForMember(current, roomId);
        SocialChatMessage message = new SocialChatMessage();
        message.setRoomId(roomId);
        message.setSenderId(current.getId());
        message.setContent(content.trim());
        messages.save(message);
        SocialChatMember senderMembership = requireMember(roomId, current.getId());
        senderMembership.setLastReadAt(message.getCreatedAt());
        members.save(senderMembership);
        room.setUpdatedAt(Instant.now());
        rooms.save(room);
        return messageResponse(message);
    }

    private SocialChatRoom requireRoomForMember(UserAccount current, String roomId) {
        SocialChatRoom room = rooms.findById(roomId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        if (!members.existsByRoomIdAndUserId(roomId, current.getId())) {
            throw new NotFoundException("聊天不存在");
        }
        return room;
    }

    private RoomResponse roomResponse(SocialChatRoom room, Long viewerId) {
        List<UserAccount> roomUsers = members.findByRoomIdOrderByJoinedAtAsc(room.getId()).stream()
                .map(SocialChatMember::getUserId)
                .map(this::requireUser)
                .toList();
        String displayName = room.getName();
        if (room.getType() == SocialChatRoom.RoomType.DIRECT) {
            displayName = roomUsers.stream()
                    .filter(user -> !user.getId().equals(viewerId))
                    .map(UserAccount::getUsername)
                    .findFirst().orElse("单聊");
        }
        Optional<SocialChatMessage> last = messages.findTopByRoomIdOrderByCreatedAtDesc(room.getId());
        String ownerPublicId = room.getOwnerId() == null ? null : requireUser(room.getOwnerId()).getPublicId();
        return new RoomResponse(room.getId(), room.getType(), displayName, ownerPublicId,
                roomUsers.stream().map(PublicUser::from).toList(),
                last.map(SocialChatMessage::getContent).orElse(null),
                last.map(SocialChatMessage::getCreatedAt).orElse(null),
                unreadCount(requireMember(room.getId(), viewerId), viewerId),
                room.getCreatedAt(), room.getUpdatedAt());
    }

    private MessageResponse messageResponse(SocialChatMessage message) {
        return new MessageResponse(message.getId(), message.getRoomId(),
                PublicUser.from(requireUser(message.getSenderId())), message.getContent(), message.getCreatedAt());
    }

    private UserAccount requirePublicUser(String publicId) {
        return users.findByPublicIdIgnoreCaseAndEnabledTrue(publicId.trim())
                .orElseThrow(() -> new NotFoundException("未找到该用户 ID"));
    }

    private UserAccount requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("用户不存在"));
    }

    private SocialChatMember requireMember(String roomId, Long userId) {
        return members.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new NotFoundException("聊天不存在"));
    }

    private long unreadCount(SocialChatMember member, Long userId) {
        Instant readBoundary = member.getLastReadAt() == null ? member.getJoinedAt() : member.getLastReadAt();
        return messages.countByRoomIdAndSenderIdNotAndCreatedAtAfter(member.getRoomId(), userId, readBoundary);
    }

    private void addMember(String roomId, Long userId) {
        if (members.existsByRoomIdAndUserId(roomId, userId)) return;
        SocialChatMember member = new SocialChatMember();
        member.setRoomId(roomId);
        member.setUserId(userId);
        members.save(member);
    }

    private String directKey(Long first, Long second) {
        long lower = Math.min(first, second);
        long higher = Math.max(first, second);
        return lower + ":" + higher;
    }
}
