package com.classpulse.classroom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {

    @Query("SELECT c FROM Classroom c JOIN FETCH c.teacher WHERE c.teacher.id = :teacherId AND c.isArchived = false")
    List<Classroom> findByTeacher_IdAndIsArchivedFalse(@Param("teacherId") UUID teacherId);

    Optional<Classroom> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);

    boolean existsByIdAndTeacher_Id(UUID id, UUID teacherId);

    @Query("SELECT c FROM Classroom c " +
           "JOIN FETCH c.teacher " +
           "JOIN ClassroomMembership m ON m.classroom = c " +
           "WHERE m.student.id = :studentId AND m.isActive = true AND c.isArchived = false")
    List<Classroom> findByStudentId(@Param("studentId") UUID studentId);
}
