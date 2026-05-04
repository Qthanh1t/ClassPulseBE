package com.classpulse.question;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, UUID> {

    @Query("SELECT a FROM StudentAnswer a JOIN FETCH a.student WHERE a.question.id = :questionId")
    List<StudentAnswer> findByQuestion_Id(@Param("questionId") UUID questionId);

    Optional<StudentAnswer> findByQuestion_IdAndStudent_Id(UUID questionId, UUID studentId);

    boolean existsByQuestion_IdAndStudent_Id(UUID questionId, UUID studentId);
}
