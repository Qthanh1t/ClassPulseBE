package com.classpulse.breakout;

import java.time.Instant;
import java.util.UUID;

public record BreakoutEndResponse(UUID breakoutSessionId, Instant endedAt) {}
