package com.classpulse.post;

import com.classpulse.classroom.ClassroomRepository;
import com.classpulse.common.exception.BusinessException;
import com.classpulse.common.exception.ForbiddenException;
import com.classpulse.common.exception.NotFoundException;
import com.classpulse.common.response.PageMeta;
import com.classpulse.user.User;
import com.classpulse.user.UserRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private static final long MAX_ATTACHMENT_SIZE = 50L * 1024 * 1024;

    private final PostRepository postRepository;
    private final PostAttachmentRepository attachmentRepository;
    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;
    private final MinioClient minioClient;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.bucket}")
    private String minioBucket;

    // T043 — list posts (paginated, newest first)
    @Transactional(readOnly = true)
    public Map.Entry<List<PostDto>, PageMeta> list(UUID classroomId, int page, int limit) {
        PageRequest pageable = PageRequest.of(page - 1, limit);
        Page<Post> result = postRepository.findByClassroomId(classroomId, pageable);
        String base = minioBase();
        List<PostDto> dtos = result.getContent().stream().map(p -> PostDto.from(p, base)).toList();
        return Map.entry(dtos, PageMeta.from(result));
    }

    // T043 — create post
    @Transactional
    public PostDto create(UUID classroomId, UUID authorId, CreatePostRequest request) {
        if (!classroomRepository.existsById(classroomId)) {
            throw new NotFoundException("Classroom not found");
        }
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        var classroom = classroomRepository.getReferenceById(classroomId);

        Post post = Post.builder()
                .classroom(classroom)
                .author(author)
                .content(request.getContent())
                .build();

        postRepository.save(post);
        log.info("User {} created post {} in classroom {}", authorId, post.getId(), classroomId);
        return PostDto.from(post, minioBase());
    }

    // T043 — update post (author or teacher only)
    @Transactional
    public PostDto update(UUID classroomId, UUID postId, UUID userId, UpdatePostRequest request) {
        Post post = findPost(postId, classroomId);
        assertCanEdit(post, userId);
        post.setContent(request.getContent());
        postRepository.save(post);
        log.info("User {} updated post {} in classroom {}", userId, postId, classroomId);
        return PostDto.from(post, minioBase());
    }

    // T043 — delete post (author or teacher only)
    @Transactional
    public void delete(UUID classroomId, UUID postId, UUID userId) {
        Post post = findPost(postId, classroomId);
        assertCanEdit(post, userId);
        postRepository.delete(post);
        log.info("User {} deleted post {} from classroom {}", userId, postId, classroomId);
    }

    // T043 — add attachments (author or teacher only)
    @Transactional
    public List<PostDto.AttachmentDto> addAttachments(UUID classroomId, UUID postId, UUID userId, List<MultipartFile> files) {
        Post post = findPost(postId, classroomId);
        assertCanEdit(post, userId);

        List<PostAttachment> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            validateFile(file);
            String ext = extractExt(file.getOriginalFilename());
            String objectKey = "post-attachments/" + postId + "/" + UUID.randomUUID() + "." + ext;

            try (var is = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(objectKey)
                        .stream(is, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            } catch (Exception e) {
                log.error("Attachment upload failed for post {}: {}", postId, e.getMessage());
                throw new BusinessException("UPLOAD_FAILED", "Failed to upload: " + file.getOriginalFilename());
            }

            PostAttachment attachment = PostAttachment.builder()
                    .post(post)
                    .fileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file." + ext)
                    .storageKey(objectKey)
                    .fileSizeBytes(file.getSize())
                    .fileExt(ext)
                    .build();
            saved.add(attachmentRepository.save(attachment));
        }

        log.info("Added {} attachments to post {} by user {}", saved.size(), postId, userId);
        String base = minioBase();
        return saved.stream().map(a -> PostDto.AttachmentDto.from(a, base + "/" + a.getStorageKey())).toList();
    }

    // T043 — delete one attachment (author or teacher only)
    @Transactional
    public void deleteAttachment(UUID classroomId, UUID postId, UUID attachmentId, UUID userId) {
        Post post = findPost(postId, classroomId);
        assertCanEdit(post, userId);

        PostAttachment attachment = attachmentRepository.findByIdAndPost_Id(attachmentId, postId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));

        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioBucket)
                    .object(attachment.getStorageKey())
                    .build());
        } catch (Exception e) {
            log.warn("Could not remove attachment from MinIO: {}", attachment.getStorageKey());
        }

        attachmentRepository.delete(attachment);
        log.info("Deleted attachment {} from post {} by user {}", attachmentId, postId, userId);
    }

    // --- helpers ---

    private Post findPost(UUID postId, UUID classroomId) {
        return postRepository.findByIdAndClassroomId(postId, classroomId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
    }

    private void assertCanEdit(Post post, UUID userId) {
        boolean isAuthor = post.getAuthor().getId().equals(userId);
        boolean isTeacher = classroomRepository.existsByIdAndTeacher_Id(post.getClassroom().getId(), userId);
        if (!isAuthor && !isTeacher) {
            throw new ForbiddenException("Not authorized to modify this post");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "Attachment file cannot be empty");
        }
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new BusinessException("FILE_TOO_LARGE", "Each attachment must be smaller than 50 MB");
        }
    }

    private static String extractExt(String filename) {
        if (filename == null || !filename.contains(".")) return "bin";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String minioBase() {
        return minioEndpoint + "/" + minioBucket;
    }
}
