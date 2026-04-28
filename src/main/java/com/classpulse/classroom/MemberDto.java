package com.classpulse.classroom;

import com.classpulse.user.User;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class MemberDto {

    private UUID id;
    private String name;
    private String avatarColor;
    private String role;
    private Instant joinedAt;

    public static MemberDto from(ClassroomMembership membership) {
        User student = membership.getStudent();
        return MemberDto.builder()
                .id(student.getId())
                .name(student.getName())
                .avatarColor(student.getAvatarColor())
                .role(student.getRole().name().toLowerCase())
                .joinedAt(membership.getJoinedAt())
                .build();
    }
}
