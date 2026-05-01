package com.classpulse.document;

import com.classpulse.classroom.ClassroomRepository;
import com.classpulse.common.exception.BusinessException;
import com.classpulse.common.exception.ForbiddenException;
import com.classpulse.common.exception.NotFoundException;
import com.classpulse.common.response.PageMeta;
import com.classpulse.post.PostAttachment;
import com.classpulse.post.PostAttachmentRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    private final ClassroomDocumentRepository documentRepository;
    private final PostAttachmentRepository attachmentRepository;
    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;
    private final MinioClient minioClient;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.bucket}")
    private String minioBucket;

    // T050 — list documents (merged from both sources)
    @Transactional(readOnly = true)
    public Map.Entry<List<DocumentDto>, PageMeta> list(UUID classroomId, String source, int page, int limit) {
        String base = minioBase();

        if ("direct".equals(source)) {
            Page<ClassroomDocument> result = documentRepository.findByClassroomId(classroomId, PageRequest.of(page - 1, limit));
            List<DocumentDto> dtos = result.getContent().stream().map(d -> DocumentDto.fromDirect(d, base)).toList();
            return Map.entry(dtos, PageMeta.from(result));
        }

        if ("post".equals(source)) {
            Page<PostAttachment> result = attachmentRepository.findByPost_ClassroomId(classroomId, PageRequest.of(page - 1, limit));
            List<DocumentDto> dtos = result.getContent().stream().map(a -> DocumentDto.fromPost(a, base)).toList();
            return Map.entry(dtos, PageMeta.from(result));
        }

        // Merge both sources, sort by uploadedAt DESC, then paginate in memory
        List<DocumentDto> merged = new ArrayList<>();
        documentRepository.findAllByClassroomId(classroomId)
                .stream().map(d -> DocumentDto.fromDirect(d, base)).forEach(merged::add);
        attachmentRepository.findAllByPost_ClassroomId(classroomId)
                .stream().map(a -> DocumentDto.fromPost(a, base)).forEach(merged::add);
        merged.sort(Comparator.comparing(DocumentDto::getUploadedAt).reversed());

        int total = merged.size();
        int fromIdx = (page - 1) * limit;
        int toIdx = Math.min(fromIdx + limit, total);
        List<DocumentDto> paged = fromIdx >= total ? List.of() : merged.subList(fromIdx, toIdx);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / limit);
        return Map.entry(paged, new PageMeta(page - 1, limit, total, totalPages));
    }

    // T050 — upload direct documents (teacher only)
    @Transactional
    public List<DocumentDto> upload(UUID classroomId, UUID uploaderId, List<MultipartFile> files) {
        if (!classroomRepository.existsById(classroomId)) {
            throw new NotFoundException("Classroom not found");
        }
        User uploader = userRepository.findById(uploaderId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        var classroom = classroomRepository.getReferenceById(classroomId);

        List<ClassroomDocument> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            validateFile(file);
            String ext = extractExt(file.getOriginalFilename());
            String objectKey = "classroom-documents/" + classroomId + "/" + UUID.randomUUID() + "." + ext;

            try (var is = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(objectKey)
                        .stream(is, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            } catch (Exception e) {
                log.error("Document upload failed for classroom {}: {}", classroomId, e.getMessage());
                throw new BusinessException("UPLOAD_FAILED", "Failed to upload: " + file.getOriginalFilename());
            }

            ClassroomDocument doc = ClassroomDocument.builder()
                    .classroom(classroom)
                    .uploader(uploader)
                    .fileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file." + ext)
                    .storageKey(objectKey)
                    .fileSizeBytes(file.getSize())
                    .fileExt(ext)
                    .build();
            saved.add(documentRepository.save(doc));
        }

        log.info("User {} uploaded {} documents to classroom {}", uploaderId, saved.size(), classroomId);
        String base = minioBase();
        return saved.stream().map(d -> DocumentDto.fromDirect(d, base)).toList();
    }

    // T050 — delete direct document (teacher/owner only)
    @Transactional
    public void delete(UUID classroomId, UUID documentId, UUID userId) {
        ClassroomDocument doc = documentRepository.findByIdAndClassroom_Id(documentId, classroomId)
                .orElseThrow(() -> new NotFoundException("Document not found"));

        if (!classroomRepository.existsByIdAndTeacher_Id(classroomId, userId)) {
            throw new ForbiddenException("Only the classroom teacher can delete documents");
        }

        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioBucket)
                    .object(doc.getStorageKey())
                    .build());
        } catch (Exception e) {
            log.warn("Could not remove document from MinIO: {}", doc.getStorageKey());
        }

        documentRepository.delete(doc);
        log.info("User {} deleted document {} from classroom {}", userId, documentId, classroomId);
    }

    // --- helpers ---

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "File cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("FILE_TOO_LARGE", "Each file must be smaller than 50 MB");
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
