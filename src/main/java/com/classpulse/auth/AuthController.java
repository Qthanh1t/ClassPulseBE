package com.classpulse.auth;

import com.classpulse.common.response.ApiResponse;
import com.classpulse.common.security.UserPrincipal;
import com.classpulse.common.security.WsTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(30);

    private final AuthService authService;
    private final WsTicketService wsTicketService;

    @Value("${jwt.refresh-token-expiry-days:30}")
    private long refreshTokenExpiryDays;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @Operation(summary = "Register a new account [PUBLIC]")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResult result = authService.register(request);
        return buildAuthResponse(result);
    }

    @Operation(summary = "Login [PUBLIC]")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authService.login(request);
        return buildAuthResponse(result);
    }

    @Operation(summary = "Refresh access token [PUBLIC]")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("MISSING_REFRESH_TOKEN", "Refresh token cookie is missing"));
        }
        AuthResult result = authService.refresh(rawRefreshToken);
        return buildAuthResponse(result);
    }

    @Operation(summary = "Logout [PUBLIC]")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            authService.logout(rawRefreshToken);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .body(ApiResponse.ok());
    }

    @Operation(summary = "Issue WebSocket ticket [AUTH]")
    @PostMapping("/ws-ticket")
    public ResponseEntity<ApiResponse<Map<String, String>>> wsTicket(
            @AuthenticationPrincipal UserPrincipal principal) {
        String ticket = wsTicketService.generateTicket(principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("ticket", ticket)));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> buildAuthResponse(AuthResult result) {
        ResponseCookie cookie = refreshCookie(result.rawRefreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(result.authResponse()));
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(COOKIE_MAX_AGE)
                .build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }
}
