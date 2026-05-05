package com.classpulse.question;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StudentAnswerDto(
        UUID id,
        UUID questionId,
        StudentInfo student,
        List<UUID> selectedOptionIds,
        String essayText,
        ConfidenceLevel confidence,
        Boolean isCorrect,
        Instant answeredAt
) {
    public record StudentInfo(UUID id, String name) {}

    public static StudentAnswerDto from(StudentAnswer answer) {
        UUID[] raw = answer.getSelectedOptionIds();
        List<UUID> optionIds = (raw != null) ? List.of(raw) : List.of();
        return new StudentAnswerDto(
                answer.getId(),
                answer.getQuestion().getId(),
                new StudentInfo(answer.getStudent().getId(), answer.getStudent().getName()),
                optionIds,
                answer.getEssayText(),
                answer.getConfidence(),
                answer.getCorrect(),
                answer.getAnsweredAt()
        );
    }
}
