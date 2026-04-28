package com.classpulse.classroom;

import java.time.Instant;
import java.util.UUID;

public record JoinResponse(UUID classroomId, String classroomName, Instant joinedAt) {}
