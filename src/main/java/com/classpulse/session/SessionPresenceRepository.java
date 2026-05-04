package com.classpulse.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionPresenceRepository extends JpaRepository<SessionPresence, SessionPresenceId> {

    @Query("SELECT p FROM SessionPresence p JOIN FETCH p.student WHERE p.session.id = :sessionId")
    List<SessionPresence> findBySessionId(@Param("sessionId") UUID sessionId);

    Optional<SessionPresence> findById_SessionIdAndId_StudentId(UUID sessionId, UUID studentId);

    @Query("SELECT p.id.studentId FROM SessionPresence p WHERE p.session.id = :sessionId AND p.leftAt IS NULL")
    List<UUID> findActiveStudentIds(@Param("sessionId") UUID sessionId);

    long countById_SessionId(UUID sessionId);

    @Modifying
    @Query("UPDATE SessionPresence p SET p.leftAt = :leftAt WHERE p.session.id = :sessionId AND p.student.id = :studentId")
    int updateLeftAt(@Param("sessionId") UUID sessionId, @Param("studentId") UUID studentId, @Param("leftAt") Instant leftAt);
}
