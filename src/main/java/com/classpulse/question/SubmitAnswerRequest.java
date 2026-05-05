package com.classpulse.question;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SubmitAnswerRequest(
        List<UUID> selectedOptionIds,

        @Size(max = 5000)
        String essayText,

        ConfidenceLevel confidence
) {}
