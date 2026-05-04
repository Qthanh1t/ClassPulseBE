package com.classpulse.question;

import com.classpulse.user.User;
import io.hypersistence.utils.hibernate.type.array.UUIDArrayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_answers")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Type(UUIDArrayType.class)
    @Column(name = "selected_option_ids", columnDefinition = "uuid[]")
    private UUID[] selectedOptionIds;

    @Column(name = "essay_text", columnDefinition = "TEXT")
    private String essayText;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ConfidenceLevel confidence;

    @Column(name = "is_correct")
    private Boolean correct;

    @CreatedDate
    @Column(name = "answered_at", updatable = false, nullable = false)
    private Instant answeredAt;
}
