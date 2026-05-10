package com.classpulse.classroom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    long countByIsArchivedFalse();

    long countByIsArchivedTrue();

    @Query(value = "SELECT c FROM Classroom c JOIN FETCH c.teacher ORDER BY c.createdAt DESC",
           countQuery = "SELECT COUNT(c) FROM Classroom c")
    Page<Classroom> findAllWithTeacher(Pageable pageable);

    @Query(value = "SELECT c FROM Classroom c JOIN FETCH c.teacher WHERE " +
                   "LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "OR LOWER(COALESCE(c.subject, '')) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "OR LOWER(c.teacher.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "ORDER BY c.createdAt DESC",
           countQuery = "SELECT COUNT(c) FROM Classroom c JOIN c.teacher t WHERE " +
                        "LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "OR LOWER(COALESCE(c.subject, '')) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Classroom> findAllFiltered(@Param("search") String search, Pageable pageable);
}
