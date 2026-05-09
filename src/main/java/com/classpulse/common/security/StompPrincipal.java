package com.classpulse.common.security;

import com.classpulse.user.Role;

import java.security.Principal;
import java.util.UUID;

public record StompPrincipal(UUID userId, Role role, String displayName) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }
}
