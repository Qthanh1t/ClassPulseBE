package com.classpulse.breakout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BreakoutSessionRepository extends JpaRepository<BreakoutSession, UUID> {

    @Query("SELECT bs FROM BreakoutSession bs WHERE bs.session.id = :sessionId AND bs.endedAt IS NULL")
    Optional<BreakoutSession> findActiveBySessionId(@Param("sessionId") UUID sessionId);

    Optional<BreakoutSession> findByIdAndSession_Id(UUID id, UUID sessionId);
}
