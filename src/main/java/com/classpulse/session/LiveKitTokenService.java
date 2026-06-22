package com.classpulse.session;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sinh LiveKit access token (JWT HS256) cho client kết nối SFU.
 *
 * <p>LiveKit token chỉ là một JWT ký bằng api-secret với claim {@code video} chứa grant.
 * Ta tự ký bằng JJWT (đã có sẵn) để không thêm dependency. Secret PHẢI >= 32 byte
 * (yêu cầu của {@link Keys#hmacShaKeyFor} cho HS256) và khớp block {@code keys:} trong livekit.yaml.</p>
 *
 * <p>{@code identity = userId} → frontend map participant.identity về presence/STOMP để
 * lấy tên, màu avatar, vai trò — không cần nhồi thêm vào token.</p>
 */
@Slf4j
@Service
public class LiveKitTokenService {

    private final String apiKey;
    private final SecretKey signingKey;
    private final String serverUrl;
    private final long tokenTtlSeconds;

    public LiveKitTokenService(
            @Value("${livekit.api-key}") String apiKey,
            @Value("${livekit.api-secret}") String apiSecret,
            @Value("${livekit.url}") String serverUrl,
            @Value("${livekit.token-ttl:21600}") long tokenTtlSeconds) {
        this.apiKey = apiKey;
        this.signingKey = Keys.hmacShaKeyFor(apiSecret.getBytes(StandardCharsets.UTF_8));
        this.serverUrl = serverUrl;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    /**
     * Sinh token cho phép {@code userId} join + publish + subscribe trong đúng {@code roomName}.
     */
    public LiveKitTokenResponse generate(UUID userId, String displayName, String roomName) {
        Instant now = Instant.now();

        // VideoGrant — tên field theo đúng spec LiveKit (camelCase)
        Map<String, Object> videoGrant = new LinkedHashMap<>();
        videoGrant.put("roomJoin", true);
        videoGrant.put("room", roomName);
        videoGrant.put("canPublish", true);
        videoGrant.put("canSubscribe", true);
        videoGrant.put("canPublishData", true);

        String token = Jwts.builder()
                .issuer(apiKey)                  // iss = api-key → LiveKit tra cứu secret để verify
                .subject(userId.toString())      // sub = identity
                .claim("name", displayName)
                .claim("video", videoGrant)
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(now.plusSeconds(tokenTtlSeconds)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        log.debug("Issued LiveKit token for user={} room={}", userId, roomName);
        return new LiveKitTokenResponse(token, serverUrl, userId.toString());
    }
}
