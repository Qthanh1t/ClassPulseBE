package com.classpulse.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PresenceDto {

    private UUID studentId;
    private String name;
    private String avatarColor;
    private Instant joinedAt;
    private boolean isOnline;

    public static PresenceDto from(SessionPresence presence, boolean isOnline) {
        return PresenceDto.builder()
                .studentId(presence.getStudent().getId())
                .name(presence.getStudent().getName())
                .avatarColor(presence.getStudent().getAvatarColor())
                .joinedAt(presence.getJoinedAt())
                .isOnline(isOnline)
                .build();
    }
}
