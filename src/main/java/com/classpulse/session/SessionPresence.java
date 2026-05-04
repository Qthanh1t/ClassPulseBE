package com.classpulse.session;

import com.classpulse.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "session_presences")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionPresence {

    @EmbeddedId
    private SessionPresenceId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("sessionId")
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("studentId")
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @CreatedDate
    @Column(name = "joined_at", updatable = false, nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;
}
