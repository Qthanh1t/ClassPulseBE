package com.classpulse.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum Role {
    TEACHER, STUDENT, ADMIN;

    // Accept the lowercase role the API emits (e.g. "teacher") in request bodies — Jackson is
    // case-sensitive for enums by default, which otherwise rejects the payload as MALFORMED_REQUEST.
    @JsonCreator
    public static Role fromJson(String value) {
        return value == null ? null : Role.valueOf(value.trim().toUpperCase());
    }

    @Converter(autoApply = true)
    public static class RoleConverter implements AttributeConverter<Role, String> {

        @Override
        public String convertToDatabaseColumn(Role role) {
            return role == null ? null : role.name().toLowerCase();
        }

        @Override
        public Role convertToEntityAttribute(String value) {
            return value == null ? null : Role.valueOf(value.toUpperCase());
        }
    }
}
