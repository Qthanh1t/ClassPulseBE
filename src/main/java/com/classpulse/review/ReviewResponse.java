package com.classpulse.review;

import com.classpulse.question.ConfidenceLevel;
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
public class ReviewResponse {

    private UUID sessionId;
    private Instant startedAt;
    private Instant endedAt;
    private int totalQuestions;
    private int answeredCount;
    private int correctCount;
    private int skippedCount;
    private BigDecimal scorePercent;
    private List<QuestionReview> questions;

    public enum ReviewResult {
        correct, wrong, skipped, pending_review
    }

    public record QuestionReview(
            UUID id,
            short questionOrder,
            QuestionType type,
            String content,
            List<UUID> mySelectedOptionIds,
            String myEssayText,
            ConfidenceLevel confidence,
            List<OptionReview> options,
            ReviewResult result
    ) {}

    public record OptionReview(
            UUID id,
            String label,
            String text,
            boolean correct,
            boolean selectedByMe
    ) {}
}
