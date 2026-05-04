package com.classpulse.session;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SessionPresenceId implements Serializable {

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;
}
