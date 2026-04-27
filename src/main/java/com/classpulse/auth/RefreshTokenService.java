package com.classpulse.auth;

import com.classpulse.common.exception.UnauthorizedException;
import com.classpulse.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiry-days}")
    private long refreshTokenExpiryDays;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public String createRefreshToken(User user) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256Hex(rawToken))
                .expiresAt(Instant.now().plus(refreshTokenExpiryDays, ChronoUnit.DAYS))
                .build();

        refreshTokenRepository.save(token);
        log.info("Created refresh token for user {}", user.getId());
        return rawToken;
    }

    @Transactional
    public RotationResult validateAndConsume(String rawToken) {
        String hash = sha256Hex(rawToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            existing.setRevoked(true);
            refreshTokenRepository.save(existing);
            throw new UnauthorizedException("Refresh token expired");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        String newRawToken = createRefreshToken(existing.getUser());
        return new RotationResult(existing.getUser().getId(), newRawToken);
    }

    @Transactional
    public void revokeByRawToken(String rawToken) {
        String hash = sha256Hex(rawToken);
        refreshTokenRepository.findByTokenHashAndRevokedFalse(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("Revoked refresh token for user {}", token.getUser().getId());
        });
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Revoked all refresh tokens for user {}", userId);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RotationResult(UUID userId, String newRawToken) {}
}
