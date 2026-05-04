package com.classpulse.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionDto {

    private UUID id;
    private UUID classroomId;
    private String classroomName;
    private UUID scheduleId;
    private String scheduleTitle;
    private String status;
    private Instant startedAt;
    private Instant endedAt;
    private Integer questionCount;
    private Integer studentCount;
    private String wsTicket;

    public static SessionDto forStart(Session session, String wsTicket) {
        return SessionDto.builder()
                .id(session.getId())
                .classroomId(session.getClassroom().getId())
                .classroomName(session.getClassroom().getName())
                .scheduleId(session.getSchedule() != null ? session.getSchedule().getId() : null)
                .status(session.getStatus().name())
                .startedAt(session.getStartedAt())
                .wsTicket(wsTicket)
                .build();
    }

    public static SessionDto forListItem(Session session, int questionCount, int studentCount) {
        return SessionDto.builder()
                .id(session.getId())
                .scheduleId(session.getSchedule() != null ? session.getSchedule().getId() : null)
                .scheduleTitle(session.getSchedule() != null ? session.getSchedule().getTitle() : null)
                .status(session.getStatus().name())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .questionCount(questionCount)
                .studentCount(studentCount)
                .build();
    }
}
