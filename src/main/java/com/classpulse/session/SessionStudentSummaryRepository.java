package com.classpulse.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SessionStudentSummaryRepository extends JpaRepository<SessionStudentSummary, SessionStudentSummaryId> {

    @Query("SELECT s FROM SessionStudentSummary s JOIN FETCH s.student WHERE s.session.id = :sessionId ORDER BY s.scorePercent DESC")
    List<SessionStudentSummary> findBySession_Id(@Param("sessionId") UUID sessionId);
}
