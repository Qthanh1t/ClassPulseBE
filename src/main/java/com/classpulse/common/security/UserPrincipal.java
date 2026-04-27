package com.classpulse.common.security;

import com.classpulse.user.Role;

import java.util.UUID;

public record UserPrincipal(UUID userId, Role role, String name) {}
