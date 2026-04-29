package com.classpulse.classroom;

import com.classpulse.schedule.Schedule;
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

    @Getter
    @Builder
    public static class NextSchedule {
        private UUID id;
        private String title;
        private String scheduledDate;
        private String startTime;
        private String endTime;

        public static NextSchedule from(Schedule s) {
            return NextSchedule.builder()
                    .id(s.getId())
                    .title(s.getTitle())
                    .scheduledDate(s.getScheduledDate().toString())
                    .startTime(String.format("%02d:%02d", s.getStartTime().getHour(), s.getStartTime().getMinute()))
                    .endTime(String.format("%02d:%02d", s.getEndTime().getHour(), s.getEndTime().getMinute()))
                    .build();
        }
    }

    public static ClassroomDto from(Classroom classroom, int studentCount) {
        return from(classroom, studentCount, null);
    }

    public static ClassroomDto from(Classroom classroom, int studentCount, Schedule nextSchedule) {
        return ClassroomDto.builder()
                .id(classroom.getId())
                .name(classroom.getName())
                .description(classroom.getDescription())
                .subject(classroom.getSubject())
                .joinCode(classroom.getJoinCode())
                .teacher(TeacherInfo.from(classroom.getTeacher()))
                .studentCount(studentCount)
                .nextSchedule(nextSchedule != null ? NextSchedule.from(nextSchedule) : null)
                .isArchived(classroom.isArchived())
                .createdAt(classroom.getCreatedAt())
                .build();
    }
}
