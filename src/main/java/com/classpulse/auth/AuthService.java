package com.classpulse.auth;

import com.classpulse.common.exception.ConflictException;
import com.classpulse.common.exception.NotFoundException;
import com.classpulse.common.exception.UnauthorizedException;
import com.classpulse.common.security.JwtTokenProvider;
import com.classpulse.user.User;
import com.classpulse.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResult register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("EMAIL_TAKEN", "Email is already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(request.getRole())
                .build();
        userRepository.save(user);
        log.info("Registered new user: {} ({})", user.getEmail(), user.getRole());

        return buildAuthResult(user);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResult(user);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.validateAndConsume(rawRefreshToken);

        User user = userRepository.findById(rotation.userId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole(), user.getName());
        AuthResponse response = AuthResponse.builder()
                .user(AuthResponse.UserSummary.from(user))
                .accessToken(accessToken)
                .expiresIn((int) jwtTokenProvider.getAccessTokenExpirySeconds())
                .build();

        return new AuthResult(response, rotation.newRawToken());
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeByRawToken(rawRefreshToken);
    }

    private AuthResult buildAuthResult(User user) {
        String rawRefreshToken = refreshTokenService.createRefreshToken(user);
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole(), user.getName());
        AuthResponse response = AuthResponse.builder()
                .user(AuthResponse.UserSummary.from(user))
                .accessToken(accessToken)
                .expiresIn((int) jwtTokenProvider.getAccessTokenExpirySeconds())
                .build();
        return new AuthResult(response, rawRefreshToken);
    }
}
