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

    public String generateTicket(UUID userId) {
        String ticket = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + ticket, userId.toString(), TTL);
        log.info("Generated WS ticket for user {}", userId);
        return ticket;
    }

    public Optional<UUID> validateAndConsume(String ticket) {
        String value = stringRedisTemplate.opsForValue().getAndDelete(KEY_PREFIX + ticket);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(value));
    }
}
