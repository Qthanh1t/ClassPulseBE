package com.classpulse.session;

import java.time.Instant;
import java.util.UUID;

public record SessionEndResponse(UUID sessionId, UUID classroomId, Instant endedAt, long duration, int questionCount, int studentCount) {}
