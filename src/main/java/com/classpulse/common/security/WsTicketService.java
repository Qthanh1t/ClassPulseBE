package com.classpulse.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WsTicketService {

    private static final String KEY_PREFIX = "ws_ticket:";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate stringRedisTemplate;

    /** Ticket without session context (used by AuthController /ws-ticket). */
    public String generateTicket(UUID userId) {
        return store(userId.toString());
    }

    /** Ticket that embeds the sessionId for presence tracking on connect. */
    public String generateSessionTicket(UUID userId, UUID sessionId) {
        return store(userId + ":" + sessionId);
    }

    public Optional<WsTicketData> validateAndConsume(String ticket) {
        String value = stringRedisTemplate.opsForValue().getAndDelete(KEY_PREFIX + ticket);
        if (value == null) return Optional.empty();
        String[] parts = value.split(":", 2);
        UUID userId = UUID.fromString(parts[0]);
        UUID sessionId = parts.length == 2 ? UUID.fromString(parts[1]) : null;
        return Optional.of(new WsTicketData(userId, sessionId));
    }

    private String store(String value) {
        String ticket = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + ticket, value, TTL);
        log.debug("Generated WS ticket (value={})", value.split(":")[0]);
        return ticket;
    }

    public record WsTicketData(UUID userId, UUID sessionId) {}
}
