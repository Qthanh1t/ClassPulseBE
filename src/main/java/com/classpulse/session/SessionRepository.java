package com.classpulse.session;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    @Query(value = "SELECT s FROM Session s JOIN FETCH s.teacher WHERE s.classroom.id = :classroomId ORDER BY s.createdAt DESC",
           countQuery = "SELECT COUNT(s) FROM Session s WHERE s.classroom.id = :classroomId")
    Page<Session> findByClassroomId(@Param("classroomId") UUID classroomId, Pageable pageable);

    @Query("SELECT s FROM Session s WHERE s.classroom.id = :classroomId AND s.status = com.classpulse.session.SessionStatus.active")
    Optional<Session> findActiveByClassroomId(@Param("classroomId") UUID classroomId);

    @Query("SELECT s.teacher.id FROM Session s WHERE s.id = :sessionId")
    Optional<UUID> findTeacherIdById(@Param("sessionId") UUID sessionId);

    Optional<Session> findByIdAndClassroom_Id(UUID id, UUID classroomId);
}
