package com.classpulse.breakout;

import java.time.Instant;
import java.util.UUID;

public record JoinRoomResponse(UUID roomId, String roomName, Instant joinedAt) {}
