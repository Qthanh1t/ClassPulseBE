package com.classpulse.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum Role {
    TEACHER, STUDENT, ADMIN;

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
