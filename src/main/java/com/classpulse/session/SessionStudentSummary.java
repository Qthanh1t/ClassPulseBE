package com.classpulse.session;

import com.classpulse.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "session_student_summaries")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionStudentSummary {

    @EmbeddedId
    private SessionStudentSummaryId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("sessionId")
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("studentId")
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "total_questions", nullable = false)
    private short totalQuestions;

    @Column(name = "answered_count", nullable = false)
    private short answeredCount;

    @Column(name = "correct_count", nullable = false)
    private short correctCount;

    @Column(name = "skipped_count", nullable = false)
    private short skippedCount;

    @Column(name = "score_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal scorePercent;

    @CreatedDate
    @Column(name = "computed_at", updatable = false, nullable = false)
    private Instant computedAt;
}
