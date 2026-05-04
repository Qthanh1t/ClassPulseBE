package com.classpulse.question;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {

    @Query("SELECT o FROM QuestionOption o WHERE o.question.id = :questionId ORDER BY o.optionOrder")
    List<QuestionOption> findByQuestionId(@Param("questionId") UUID questionId);
}
