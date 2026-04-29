package com.classpulse.post;

import com.classpulse.user.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostDto {

    private UUID id;
    private AuthorInfo author;
    private String content;
    private List<AttachmentDto> attachments;
    private Instant createdAt;
    private Instant updatedAt;

    @Getter
    @Builder
    public static class AuthorInfo {
        private UUID id;
        private String name;
        private String role;
        private String avatarColor;

        public static AuthorInfo from(User user) {
            return AuthorInfo.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .role(user.getRole().name().toLowerCase())
                    .avatarColor(user.getAvatarColor())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class AttachmentDto {
        private UUID id;
        private String fileName;
        private String fileExt;
        private long fileSizeBytes;
        private String url;
        private Instant uploadedAt;

        public static AttachmentDto from(PostAttachment attachment, String url) {
            return AttachmentDto.builder()
                    .id(attachment.getId())
                    .fileName(attachment.getFileName())
                    .fileExt(attachment.getFileExt())
                    .fileSizeBytes(attachment.getFileSizeBytes())
                    .url(url)
                    .uploadedAt(attachment.getUploadedAt())
                    .build();
        }
    }

    public static PostDto from(Post post, String minioBase) {
        List<AttachmentDto> attachments = post.getAttachments().stream()
                .map(a -> AttachmentDto.from(a, minioBase + "/" + a.getStorageKey()))
                .toList();
        return PostDto.builder()
                .id(post.getId())
                .author(AuthorInfo.from(post.getAuthor()))
                .content(post.getContent())
                .attachments(attachments)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
