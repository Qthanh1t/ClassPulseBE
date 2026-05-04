package com.classpulse.question;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionDto(
        UUID id,
        int questionOrder,
        QuestionType type,
        String content,
        Integer timerSeconds,
        QuestionStatus status,
        Instant startedAt,
        Instant endsAt,
        Instant endedAt,
        Instant createdAt,
        List<OptionDto> options
) {
    public static QuestionDto from(Question question) {
        return QuestionDto.builder()
                .id(question.getId())
                .questionOrder(question.getQuestionOrder())
                .type(question.getType())
                .content(question.getContent())
                .timerSeconds(question.getTimerSeconds())
                .status(question.getStatus())
                .startedAt(question.getStartedAt())
                .endsAt(question.getEndsAt())
                .endedAt(question.getEndedAt())
                .createdAt(question.getCreatedAt())
                .options(question.getOptions().stream().map(OptionDto::from).toList())
                .build();
    }
}
