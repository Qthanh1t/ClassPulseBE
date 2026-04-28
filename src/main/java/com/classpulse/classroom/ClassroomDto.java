package com.classpulse.classroom;

import com.classpulse.user.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClassroomDto {

    private UUID id;
    private String name;
    private String description;
    private String subject;
    private String joinCode;
    private TeacherInfo teacher;
    private int studentCount;
    private NextSchedule nextSchedule;
    private boolean isArchived;
    private Instant createdAt;

    @Getter
    @Builder
    public static class TeacherInfo {
        private UUID id;
        private String name;
        private String avatarColor;

        public static TeacherInfo from(User teacher) {
            return TeacherInfo.builder()
                    .id(teacher.getId())
                    .name(teacher.getName())
                    .avatarColor(teacher.getAvatarColor())
                    .build();
        }
    }

    // Populated in M06 — always null until Schedule module is implemented
    @Getter
    @Builder
    public static class NextSchedule {
        private UUID id;
        private String title;
        private String scheduledDate;
        private String startTime;
        private String endTime;
    }

    public static ClassroomDto from(Classroom classroom, int studentCount) {
        return ClassroomDto.builder()
                .id(classroom.getId())
                .name(classroom.getName())
                .description(classroom.getDescription())
                .subject(classroom.getSubject())
                .joinCode(classroom.getJoinCode())
                .teacher(TeacherInfo.from(classroom.getTeacher()))
                .studentCount(studentCount)
                .isArchived(classroom.isArchived())
                .createdAt(classroom.getCreatedAt())
                .build();
    }
}
