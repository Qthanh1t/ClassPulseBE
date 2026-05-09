package com.classpulse.breakout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BreakoutRoomRepository extends JpaRepository<BreakoutRoom, UUID> {

    @Query("SELECT DISTINCT r FROM BreakoutRoom r LEFT JOIN FETCH r.assignments a LEFT JOIN FETCH a.student WHERE r.breakoutSession.id = :breakoutSessionId ORDER BY r.roomOrder")
    List<BreakoutRoom> findByBreakoutSession_IdWithStudents(@Param("breakoutSessionId") UUID breakoutSessionId);

    Optional<BreakoutRoom> findByIdAndBreakoutSession_Id(UUID id, UUID breakoutSessionId);
}
