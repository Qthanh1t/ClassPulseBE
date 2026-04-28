package com.classpulse.classroom;

import com.classpulse.common.BaseEntity;
import com.classpulse.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "classrooms")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Classroom extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String subject;

    @Column(name = "join_code", nullable = false, unique = true, length = 12)
    private String joinCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Column(name = "is_archived", nullable = false)
    @Builder.Default
    private boolean isArchived = false;
}
