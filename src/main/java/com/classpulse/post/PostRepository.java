package com.classpulse.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    @Query(value = "SELECT p FROM Post p JOIN FETCH p.author WHERE p.classroom.id = :classroomId ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(p) FROM Post p WHERE p.classroom.id = :classroomId")
    Page<Post> findByClassroomId(@Param("classroomId") UUID classroomId, Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.id = :id AND p.classroom.id = :classroomId")
    Optional<Post> findByIdAndClassroomId(@Param("id") UUID id, @Param("classroomId") UUID classroomId);
}
