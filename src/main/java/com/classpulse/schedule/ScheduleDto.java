package com.classpulse.schedule;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduleDto {

    private UUID id;
    private String title;
    private String scheduledDate;
    private String startTime;
    private String endTime;
    private String description;
    private UUID sessionId;
    private Instant createdAt;
    private Instant updatedAt;

    public static ScheduleDto from(Schedule s) {
        return ScheduleDto.builder()
                .id(s.getId())
                .title(s.getTitle())
                .scheduledDate(s.getScheduledDate().toString())
                .startTime(formatTime(s.getStartTime()))
                .endTime(formatTime(s.getEndTime()))
                .description(s.getDescription())
                .sessionId(null)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private static String formatTime(java.time.LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }
}
