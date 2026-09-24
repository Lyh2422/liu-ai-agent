package com.lyh.liuaiagent.social.dto;

import com.lyh.liuaiagent.auth.model.UserAccount;
import com.lyh.liuaiagent.social.model.SocialChatRoom;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class SocialDtos {
    private SocialDtos() {}

    public record PublicUser(String publicId, String username, String avatarUrl, String signature) {
        public static PublicUser from(UserAccount user) {
            return new PublicUser(user.getPublicId(), user.getUsername(), user.getAvatarUrl(), user.getSignature());
        }
    }

    public record AddByPublicIdRequest(
            @NotBlank(message = "请输入用户 ID")
            @Size(max = 20, message = "用户 ID 格式不正确") String publicId) {}

    public record CreateGroupRequest(
            @NotBlank(message = "请输入群聊名称")
            @Size(max = 80, message = "群聊名称不能超过 80 个字符") String name,
            List<String> memberPublicIds) {}

    public record RoomResponse(
            String id,
            SocialChatRoom.RoomType type,
            String name,
            String ownerPublicId,
            List<PublicUser> members,
            String lastMessage,
            Instant lastMessageAt,
            long unreadCount,
            Instant createdAt,
            Instant updatedAt) {}

    public record UnreadCountResponse(long unreadCount) {}

    public record MessageRequest(
            @NotBlank(message = "消息不能为空")
            @Size(max = 2000, message = "消息不能超过 2000 个字符") String content) {}

    public record MessageResponse(
            String id,
            String roomId,
            PublicUser sender,
            String content,
            Instant createdAt) {}
}
