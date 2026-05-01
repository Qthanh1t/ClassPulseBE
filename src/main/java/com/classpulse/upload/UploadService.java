package com.classpulse.upload;

import com.classpulse.common.exception.BusinessException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

    private static final int PRESIGN_EXPIRY_SECONDS = 300; // 5 minutes
    private static final long MAX_SIZE_AVATAR = 5L * 1024 * 1024;
    private static final long MAX_SIZE_DOCUMENT = 50L * 1024 * 1024;

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String minioBucket;

    // T051 — generate presigned PUT URLs for direct client-to-MinIO upload
    public List<PresignedUrlDto> presign(UploadPresignRequest request) {
        long maxSize = "avatar".equals(request.getPurpose()) ? MAX_SIZE_AVATAR : MAX_SIZE_DOCUMENT;

        List<PresignedUrlDto> result = new ArrayList<>();
        for (UploadPresignRequest.FileInfo fileInfo : request.getFiles()) {
            if (fileInfo.getFileSizeBytes() > maxSize) {
                throw new BusinessException("FILE_TOO_LARGE",
                        "File '" + fileInfo.getFileName() + "' exceeds the " + (maxSize / 1024 / 1024) + " MB limit");
            }

            String objectKey = buildObjectKey(fileInfo.getFileName());
            Instant expiresAt = Instant.now().plusSeconds(PRESIGN_EXPIRY_SECONDS);

            try {
                String url = minioClient.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                                .method(Method.PUT)
                                .bucket(minioBucket)
                                .object(objectKey)
                                .expiry(PRESIGN_EXPIRY_SECONDS, TimeUnit.SECONDS)
                                .build()
                );
                result.add(PresignedUrlDto.builder()
                        .fileName(fileInfo.getFileName())
                        .fileKey(objectKey)
                        .uploadUrl(url)
                        .expiresAt(expiresAt)
                        .build());
            } catch (Exception e) {
                log.error("Failed to generate presigned URL for '{}': {}", fileInfo.getFileName(), e.getMessage());
                throw new BusinessException("PRESIGN_FAILED", "Could not generate upload URL for: " + fileInfo.getFileName());
            }
        }

        log.info("Generated {} presigned URLs for purpose '{}'", result.size(), request.getPurpose());
        return result;
    }

    private static String buildObjectKey(String fileName) {
        LocalDate today = LocalDate.now();
        String year = String.valueOf(today.getYear());
        String month = String.format("%02d", today.getMonthValue());
        String sanitized = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return "uploads/" + year + "/" + month + "/" + UUID.randomUUID() + "-" + sanitized;
    }
}
