package com.classpulse.question;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    @Query("SELECT DISTINCT q FROM Question q LEFT JOIN FETCH q.options WHERE q.session.id = :sessionId ORDER BY q.questionOrder")
    List<Question> findBySessionId(@Param("sessionId") UUID sessionId);

    @Query("SELECT q FROM Question q WHERE q.session.id = :sessionId AND q.status = com.classpulse.question.QuestionStatus.running")
    Optional<Question> findRunningBySessionId(@Param("sessionId") UUID sessionId);

    Optional<Question> findByIdAndSession_Id(UUID id, UUID sessionId);

    long countBySession_Id(UUID sessionId);
}
