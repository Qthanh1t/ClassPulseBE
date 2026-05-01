package com.classpulse.document;

import com.classpulse.post.PostAttachment;
import com.classpulse.user.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentDto {

    private UUID id;
    private String fileName;
    private String fileExt;
    private long fileSizeBytes;
    private String url;
    private String source;
    private UUID postId;
    private UploaderInfo uploadedBy;
    private Instant uploadedAt;

    @Getter
    @Builder
    public static class UploaderInfo {
        private UUID id;
        private String name;

        public static UploaderInfo from(User user) {
            return UploaderInfo.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .build();
        }
    }

    public static DocumentDto fromDirect(ClassroomDocument doc, String minioBase) {
        return DocumentDto.builder()
                .id(doc.getId())
                .fileName(doc.getFileName())
                .fileExt(doc.getFileExt())
                .fileSizeBytes(doc.getFileSizeBytes())
                .url(minioBase + "/" + doc.getStorageKey())
                .source("direct")
                .uploadedBy(UploaderInfo.from(doc.getUploader()))
                .uploadedAt(doc.getUploadedAt())
                .build();
    }

    public static DocumentDto fromPost(PostAttachment attachment, String minioBase) {
        return DocumentDto.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .fileExt(attachment.getFileExt())
                .fileSizeBytes(attachment.getFileSizeBytes())
                .url(minioBase + "/" + attachment.getStorageKey())
                .source("post")
                .postId(attachment.getPost().getId())
                .uploadedBy(UploaderInfo.from(attachment.getPost().getAuthor()))
                .uploadedAt(attachment.getUploadedAt())
                .build();
    }
}
