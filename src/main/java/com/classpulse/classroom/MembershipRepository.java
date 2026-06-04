package com.classpulse.classroom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<ClassroomMembership, ClassroomMembershipId> {

    List<ClassroomMembership> findByClassroom_IdAndIsActiveTrue(UUID classroomId);

    Optional<ClassroomMembership> findByClassroom_IdAndStudent_Id(UUID classroomId, UUID studentId);

    boolean existsByClassroom_IdAndStudent_IdAndIsActiveTrue(UUID classroomId, UUID studentId);

    long countByClassroom_IdAndIsActiveTrue(UUID classroomId);

    @Query("SELECT COUNT(m) FROM ClassroomMembership m " +
           "WHERE m.student.id = :studentId AND m.isActive = true AND m.classroom.isArchived = false")
    long countActiveClassroomsByStudent(@Param("studentId") UUID studentId);

    @Query("SELECT COUNT(DISTINCT m.student.id) FROM ClassroomMembership m " +
           "WHERE m.classroom.teacher.id = :teacherId AND m.isActive = true")
    long countDistinctStudentsByTeacher(@Param("teacherId") UUID teacherId);
}
