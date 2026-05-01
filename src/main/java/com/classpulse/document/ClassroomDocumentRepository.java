package com.classpulse.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassroomDocumentRepository extends JpaRepository<ClassroomDocument, UUID> {

    @Query(value = "SELECT d FROM ClassroomDocument d JOIN FETCH d.uploader WHERE d.classroom.id = :classroomId ORDER BY d.uploadedAt DESC",
           countQuery = "SELECT COUNT(d) FROM ClassroomDocument d WHERE d.classroom.id = :classroomId")
    Page<ClassroomDocument> findByClassroomId(@Param("classroomId") UUID classroomId, Pageable pageable);

    @Query("SELECT d FROM ClassroomDocument d JOIN FETCH d.uploader WHERE d.classroom.id = :classroomId ORDER BY d.uploadedAt DESC")
    List<ClassroomDocument> findAllByClassroomId(@Param("classroomId") UUID classroomId);

    Optional<ClassroomDocument> findByIdAndClassroom_Id(UUID id, UUID classroomId);
}
