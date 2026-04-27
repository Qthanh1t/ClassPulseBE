package com.classpulse.auth;

public record AuthResult(AuthResponse authResponse, String rawRefreshToken) {}
