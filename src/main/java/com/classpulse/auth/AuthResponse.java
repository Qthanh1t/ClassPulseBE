package com.classpulse.auth;

import com.classpulse.user.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private final UserSummary user;
    private final String accessToken;
    private final int expiresIn;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserSummary {
        private final UUID id;
        private final String email;
        private final String name;
        private final String role;
        private final String avatarColor;
        private final String avatarUrl;
        private final Instant createdAt;

        public static UserSummary from(User user) {
            return UserSummary.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .name(user.getName())
                    .role(user.getRole().name().toLowerCase())
                    .avatarColor(user.getAvatarColor())
                    .avatarUrl(user.getAvatarUrl())
                    .createdAt(user.getCreatedAt())
                    .build();
        }
    }
}
