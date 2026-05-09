package com.classpulse.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    private final SessionPresenceRepository presenceRepository;
    private final SessionBroadcastService broadcastService;
    private final StringRedisTemplate redisTemplate;

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        Attrs attrs = extractAttrs(event.getMessage());
        if (attrs == null || !"STUDENT".equals(attrs.role())) return;

        redisTemplate.opsForSet().add(presenceKey(attrs.sessionId()), attrs.userId().toString());

        broadcastService.broadcastToSession(attrs.sessionId(), "student_presence",
                Map.of("studentId", attrs.userId(), "action", "joined"));

        log.info("Student {} joined session {} via WS", attrs.userId(), attrs.sessionId());
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        Attrs attrs = extractAttrs(event.getMessage());
        if (attrs == null || !"STUDENT".equals(attrs.role())) return;

        redisTemplate.opsForSet().remove(presenceKey(attrs.sessionId()), attrs.userId().toString());

        // updateLeftAt is @Modifying — Spring Data handles the transaction
        presenceRepository.updateLeftAt(attrs.sessionId(), attrs.userId(), Instant.now());

        broadcastService.broadcastToSession(attrs.sessionId(), "student_presence",
                Map.of("studentId", attrs.userId(), "action", "left"));

        log.info("Student {} left session {} via WS disconnect", attrs.userId(), attrs.sessionId());
    }

    private Attrs extractAttrs(Message<?> message) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(message);
        Map<String, Object> sessionAttrs = sha.getSessionAttributes();
        if (sessionAttrs == null) return null;

        UUID userId = parseUuid(sessionAttrs.get("userId"));
        UUID sessionId = parseUuid(sessionAttrs.get("sessionId"));
        String role = (String) sessionAttrs.get("userRole");

        if (userId == null || sessionId == null || role == null) return null;
        return new Attrs(userId, sessionId, role);
    }

    private UUID parseUuid(Object val) {
        if (val == null) return null;
        try {
            return val instanceof UUID u ? u : UUID.fromString(val.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String presenceKey(UUID sessionId) {
        return "session:" + sessionId + ":presence";
    }

    private record Attrs(UUID userId, UUID sessionId, String role) {}
}
