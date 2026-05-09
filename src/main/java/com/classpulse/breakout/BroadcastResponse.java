package com.classpulse.breakout;

import java.time.Instant;

public record BroadcastResponse(Instant sentAt, int recipientCount) {}
