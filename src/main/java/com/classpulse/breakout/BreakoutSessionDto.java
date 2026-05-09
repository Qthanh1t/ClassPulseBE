package com.classpulse.breakout;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BreakoutSessionDto(
        UUID breakoutSessionId,
        Instant startedAt,
        Instant endedAt,
        List<RoomDto> rooms
) {
    public record RoomDto(
            UUID id,
            String name,
            String task,
            int order,
            List<StudentInfo> students
    ) {}

    public record StudentInfo(
            UUID id,
            String name,
            String avatarColor
    ) {}

    public static BreakoutSessionDto from(BreakoutSession bs, List<BreakoutRoom> rooms) {
        List<RoomDto> roomDtos = rooms.stream()
                .map(r -> new RoomDto(
                        r.getId(),
                        r.getName(),
                        r.getTask(),
                        r.getRoomOrder(),
                        r.getAssignments().stream()
                                .map(a -> new StudentInfo(
                                        a.getStudent().getId(),
                                        a.getStudent().getName(),
                                        a.getStudent().getAvatarColor()))
                                .toList()))
                .toList();

        return BreakoutSessionDto.builder()
                .breakoutSessionId(bs.getId())
                .startedAt(bs.getStartedAt())
                .endedAt(bs.getEndedAt())
                .rooms(roomDtos)
                .build();
    }
}
