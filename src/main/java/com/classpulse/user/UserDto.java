package com.classpulse.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDto {

    private final UUID id;
    private final String email;
    private final String name;
    private final String role;
    private final String avatarColor;
    private final String avatarUrl;
    private final Boolean isActive;
    private final Instant createdAt;
    private final Stats stats;

    @Getter
    @Builder
    public static class Stats {
        private final int classroomsCount;
        private final int sessionsCount;
        private final int questionsAsked;
        private final int studentsReached;
    }

    public static UserDto from(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name().toLowerCase())
                .avatarColor(user.getAvatarColor())
                .avatarUrl(user.getAvatarUrl())
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public static UserDto from(User user, Stats stats) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name().toLowerCase())
                .avatarColor(user.getAvatarColor())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .stats(stats)
                .build();
    }
}
