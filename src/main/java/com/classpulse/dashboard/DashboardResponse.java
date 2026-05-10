package com.classpulse.dashboard;

import com.classpulse.question.QuestionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardResponse {

    private UUID sessionId;
    private Instant startedAt;
    private Instant endedAt;
    private long durationSeconds;
    private int totalStudents;
    private int totalQuestions;
    private OverallStats overallStats;
    private List<QuestionSummary> questions;
    private List<StudentResult> students;

    public record OverallStats(
            BigDecimal avgScorePercent,
            int participantCount
    ) {}

    public record QuestionSummary(
            UUID id,
            short questionOrder,
            QuestionType type,
            String content,
            int totalStudents,
            int answeredCount,
            int correctCount,
            int skippedCount,
            List<OptionResult> options
    ) {}

    public record OptionResult(
            UUID id,
            String label,
            String text,
            boolean correct,
            int count
    ) {}

    public record StudentResult(
            UUID studentId,
            String name,
            String avatarColor,
            short answeredCount,
            short correctCount,
            short skippedCount,
            BigDecimal scorePercent
    ) {}
}
