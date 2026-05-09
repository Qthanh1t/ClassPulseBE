package com.classpulse.chat;

import java.util.UUID;

public record ChatCursorMeta(boolean hasMore, UUID oldestId) {}
