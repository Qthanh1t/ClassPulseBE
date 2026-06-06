package com.classpulse.question;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SubmitAnswerRequest(
        List<UUID> selectedOptionIds,

        // Rich-text HTML answer (may embed image URLs and file-download links) — needs headroom.
        @Size(max = 50000)
        String essayText,

        ConfidenceLevel confidence
) {}
