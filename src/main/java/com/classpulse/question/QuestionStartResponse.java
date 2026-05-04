package com.classpulse.question;

import java.time.Instant;
import java.util.UUID;

public record QuestionStartResponse(UUID id, QuestionStatus status, Instant startedAt, Instant endsAt) {}
