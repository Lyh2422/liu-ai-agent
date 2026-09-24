package com.lyh.liuaiagent.social.controller;

import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.social.dto.SocialDtos.*;
import com.lyh.liuaiagent.social.service.SocialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/social")
public class SocialController {
    private final SocialService socialService;

    public SocialController(SocialService socialService) {
        this.socialService = socialService;
    }

    @GetMapping("/friends")
    public List<PublicUser> friends(@AuthenticationPrincipal UserAccount current) {
        return socialService.listFriends(current);
    }

    @PostMapping("/friends")
    public ResponseEntity<PublicUser> addFriend(@AuthenticationPrincipal UserAccount current,
                                                 @Valid @RequestBody AddByPublicIdRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(socialService.addFriend(current, request.publicId()));
    }

    @DeleteMapping("/friends/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFriend(@AuthenticationPrincipal UserAccount current, @PathVariable String publicId) {
        socialService.removeFriend(current, publicId);
    }

    @GetMapping("/chats")
    public List<RoomResponse> chats(@AuthenticationPrincipal UserAccount current) {
        return socialService.listRooms(current);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal UserAccount current) {
        return socialService.unreadCount(current);
    }

    @PostMapping("/chats/direct")
    public RoomResponse direct(@AuthenticationPrincipal UserAccount current,
                               @Valid @RequestBody AddByPublicIdRequest request) {
        return socialService.openDirect(current, request.publicId());
    }

    @PostMapping("/chats/groups")
    public ResponseEntity<RoomResponse> group(@AuthenticationPrincipal UserAccount current,
                                               @Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(socialService.createGroup(current, request));
    }

    @GetMapping("/chats/{roomId}")
    public RoomResponse room(@AuthenticationPrincipal UserAccount current, @PathVariable String roomId) {
        return socialService.getRoom(current, roomId);
    }

    @PostMapping("/chats/{roomId}/members")
    public RoomResponse invite(@AuthenticationPrincipal UserAccount current, @PathVariable String roomId,
                               @Valid @RequestBody AddByPublicIdRequest request) {
        return socialService.inviteMember(current, roomId, request.publicId());
    }

    @GetMapping("/chats/{roomId}/messages")
    public List<MessageResponse> messages(@AuthenticationPrincipal UserAccount current,
                                          @PathVariable String roomId) {
        return socialService.listMessages(current, roomId);
    }

    @PostMapping("/chats/{roomId}/messages")
    public ResponseEntity<MessageResponse> send(@AuthenticationPrincipal UserAccount current,
                                                 @PathVariable String roomId,
                                                 @Valid @RequestBody MessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(socialService.sendMessage(current, roomId, request.content()));
    }
}
