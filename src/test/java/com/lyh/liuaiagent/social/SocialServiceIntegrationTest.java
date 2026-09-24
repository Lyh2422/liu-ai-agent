package com.lyh.liuaiagent.social;

import com.lyh.liuaiagent.auth.exception.NotFoundException;
import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.auth.model.UserRole;
import com.lyh.liuaiagent.auth.repository.UserAccountRepository;
import com.lyh.liuaiagent.social.dto.SocialDtos.CreateGroupRequest;
import com.lyh.liuaiagent.social.repository.FriendshipRepository;
import com.lyh.liuaiagent.social.repository.SocialChatMemberRepository;
import com.lyh.liuaiagent.social.repository.SocialChatMessageRepository;
import com.lyh.liuaiagent.social.repository.SocialChatRoomRepository;
import com.lyh.liuaiagent.social.service.SocialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class SocialServiceIntegrationTest {
    @Autowired UserAccountRepository users;
    @Autowired FriendshipRepository friendships;
    @Autowired SocialChatRoomRepository rooms;
    @Autowired SocialChatMemberRepository members;
    @Autowired SocialChatMessageRepository messages;

    SocialService service;
    UserAccount alice;
    UserAccount bob;
    UserAccount carol;

    @BeforeEach
    void setUp() {
        service = new SocialService(users, friendships, rooms, members, messages);
        alice = saveUser("alice", "UALICE00001");
        bob = saveUser("bob", "UBOB0000001");
        carol = saveUser("carol", "UCAROL00001");
    }

    @Test
    void addsFriendByPublicIdAndListsItForBothUsers() {
        var added = service.addFriend(alice, bob.getPublicId());

        assertEquals("bob", added.username());
        assertEquals(List.of("bob"), service.listFriends(alice).stream().map(user -> user.username()).toList());
        assertEquals(List.of("alice"), service.listFriends(bob).stream().map(user -> user.username()).toList());
    }

    @Test
    void directChatIsReusedAndMessagesAreVisibleOnlyToMembers() {
        var first = service.openDirect(alice, bob.getPublicId());
        var second = service.openDirect(bob, alice.getPublicId());
        assertEquals(first.id(), second.id());

        service.sendMessage(alice, first.id(), "你好，Bob");
        var history = service.listMessages(bob, first.id());
        assertEquals(1, history.size());
        assertEquals("你好，Bob", history.getFirst().content());
        assertEquals("alice", history.getFirst().sender().username());
        assertThrows(NotFoundException.class, () -> service.listMessages(carol, first.id()));
    }

    @Test
    void tracksUnreadMessagesPerMemberAndClearsThemAfterReading() {
        var room = service.openDirect(alice, bob.getPublicId());

        service.sendMessage(alice, room.id(), "第一条");
        service.sendMessage(alice, room.id(), "第二条");

        assertEquals(0, service.unreadCount(alice).unreadCount());
        assertEquals(2, service.unreadCount(bob).unreadCount());
        assertEquals(2, service.listRooms(bob).getFirst().unreadCount());

        service.listMessages(bob, room.id());

        assertEquals(0, service.unreadCount(bob).unreadCount());
        assertEquals(0, service.listRooms(bob).getFirst().unreadCount());
    }

    @Test
    void groupOwnerCanInviteButOrdinaryMemberCannot() {
        var group = service.createGroup(alice, new CreateGroupRequest("学习小组", List.of(bob.getPublicId())));
        assertEquals(2, group.members().size());

        var updated = service.inviteMember(alice, group.id(), carol.getPublicId());
        assertEquals(3, updated.members().size());

        UserAccount dave = saveUser("dave", "UDAVE000001");
        assertThrows(AccessDeniedException.class,
                () -> service.inviteMember(bob, group.id(), dave.getPublicId()));
    }

    private UserAccount saveUser(String username, String publicId) {
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPublicId(publicId);
        user.setPasswordHash("not-used");
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        return users.saveAndFlush(user);
    }
}
