package com.classpulse.admin;

public record AdminStatsDto(
        long totalUsers,
        long teacherCount,
        long studentCount,
        long activeClassrooms,
        long archivedClassrooms,
        long activeSessions
) {}
