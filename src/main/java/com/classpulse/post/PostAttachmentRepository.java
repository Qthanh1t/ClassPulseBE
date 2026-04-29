package com.classpulse.post;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PostAttachmentRepository extends JpaRepository<PostAttachment, UUID> {

    Optional<PostAttachment> findByIdAndPost_Id(UUID id, UUID postId);
}
