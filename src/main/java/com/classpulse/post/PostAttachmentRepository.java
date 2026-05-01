package com.classpulse.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostAttachmentRepository extends JpaRepository<PostAttachment, UUID> {

    Optional<PostAttachment> findByIdAndPost_Id(UUID id, UUID postId);

    @Query(value = "SELECT pa FROM PostAttachment pa JOIN FETCH pa.post p JOIN FETCH p.author WHERE p.classroom.id = :classroomId ORDER BY pa.uploadedAt DESC",
           countQuery = "SELECT COUNT(pa) FROM PostAttachment pa JOIN pa.post p WHERE p.classroom.id = :classroomId")
    Page<PostAttachment> findByPost_ClassroomId(@Param("classroomId") UUID classroomId, Pageable pageable);

    @Query("SELECT pa FROM PostAttachment pa JOIN FETCH pa.post p JOIN FETCH p.author WHERE p.classroom.id = :classroomId ORDER BY pa.uploadedAt DESC")
    List<PostAttachment> findAllByPost_ClassroomId(@Param("classroomId") UUID classroomId);
}
