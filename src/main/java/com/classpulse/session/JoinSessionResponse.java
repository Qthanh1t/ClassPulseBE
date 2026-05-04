package com.classpulse.session;

import java.util.UUID;

public record JoinSessionResponse(UUID sessionId, String classroomName, String teacherName, String wsTicket) {}
