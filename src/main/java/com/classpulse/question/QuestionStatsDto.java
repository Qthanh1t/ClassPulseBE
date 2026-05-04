package com.classpulse.question;

import java.util.List;
import java.util.UUID;

public record QuestionStatsDto(
        UUID questionId,
        int totalStudents,
        int answeredCount,
        int skippedCount,
        int correctCount,
        int wrongCount,
        List<OptionDistribution> optionDistribution,
        ConfidenceBreakdown confidenceBreakdown,
        List<SilentStudent> silentStudents
) {
    public record OptionDistribution(UUID optionId, String label, String text, boolean isCorrect, int count) {}

    public record ConfidenceBreakdown(int high, int medium, int low, int none) {}

    public record SilentStudent(UUID id, String name, String avatarColor) {}
}
