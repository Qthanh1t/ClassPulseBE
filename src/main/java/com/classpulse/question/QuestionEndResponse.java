package com.classpulse.question;

import java.time.Instant;
import java.util.UUID;

public record QuestionEndResponse(UUID id, QuestionStatus status, Instant endedAt) {}
