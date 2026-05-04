package com.classpulse.question;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, UUID> {

    List<StudentAnswer> findByQuestion_Id(UUID questionId);

    Optional<StudentAnswer> findByQuestion_IdAndStudent_Id(UUID questionId, UUID studentId);

    boolean existsByQuestion_IdAndStudent_Id(UUID questionId, UUID studentId);
}
