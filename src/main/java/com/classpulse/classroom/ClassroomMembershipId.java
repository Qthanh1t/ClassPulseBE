package com.classpulse.classroom;

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
public class ClassroomMembershipId implements Serializable {

    @Column(name = "classroom_id", nullable = false)
    private UUID classroomId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;
}
