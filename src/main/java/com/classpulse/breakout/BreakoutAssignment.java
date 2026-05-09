package com.classpulse.breakout;

import com.classpulse.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "breakout_assignments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BreakoutAssignment {

    @EmbeddedId
    private BreakoutAssignmentId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("roomId")
    @JoinColumn(name = "room_id", nullable = false)
    private BreakoutRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("studentId")
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
}
