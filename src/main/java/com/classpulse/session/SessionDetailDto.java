package com.classpulse.session;

import com.classpulse.user.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionDetailDto {

    private UUID id;
    private UUID classroomId;
    private String classroomName;
    private TeacherInfo teacher;
    private String status;
    private Instant startedAt;
    private Instant endedAt;
    private int questionCount;
    private int presentStudentCount;

    @Getter
    @Builder
    public static class TeacherInfo {
        private UUID id;
        private String name;

        public static TeacherInfo from(User teacher) {
            return TeacherInfo.builder()
                    .id(teacher.getId())
                    .name(teacher.getName())
                    .build();
        }
    }

    public static SessionDetailDto from(Session session, int questionCount, int presentStudentCount) {
        return SessionDetailDto.builder()
                .id(session.getId())
                .classroomId(session.getClassroom().getId())
                .classroomName(session.getClassroom().getName())
                .teacher(TeacherInfo.from(session.getTeacher()))
                .status(session.getStatus().name())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .questionCount(questionCount)
                .presentStudentCount(presentStudentCount)
                .build();
    }
}
