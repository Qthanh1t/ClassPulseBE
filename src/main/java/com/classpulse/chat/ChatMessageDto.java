package com.classpulse.chat;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessageDto(
        UUID id,
        SenderInfo sender,
        String content,
        UUID breakoutRoomId,
        Instant sentAt
) {
    public record SenderInfo(UUID id, String name, String role, String avatarColor) {}

    public static ChatMessageDto from(ChatMessage msg) {
        var s = msg.getSender();
        return new ChatMessageDto(
                msg.getId(),
                new SenderInfo(
                        s.getId(),
                        s.getName(),
                        s.getRole().name().toLowerCase(),
                        s.getAvatarColor()),
                msg.getContent(),
                msg.getBreakoutRoom() != null ? msg.getBreakoutRoom().getId() : null,
                msg.getSentAt());
    }
}
