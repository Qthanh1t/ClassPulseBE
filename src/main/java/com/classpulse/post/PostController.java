package com.classpulse.post;

import com.classpulse.common.response.ApiResponse;
import com.classpulse.common.response.PageMeta;
import com.classpulse.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Posts")
@RestController
@RequestMapping("/api/v1/classrooms/{classroomId}/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "List posts in classroom [MEMBER]")
    @GetMapping
    @PreAuthorize("@classroomSecurity.isMember(#classroomId, authentication)")
    public ResponseEntity<ApiResponse<List<PostDto>>> list(
            @PathVariable UUID classroomId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Map.Entry<List<PostDto>, PageMeta> result = postService.list(classroomId, page, limit);
        return ResponseEntity.ok(ApiResponse.ok(result.getKey(), result.getValue()));
    }

    @Operation(summary = "Create post [MEMBER]")
    @PostMapping
    @PreAuthorize("@classroomSecurity.isMember(#classroomId, authentication)")
    public ResponseEntity<ApiResponse<PostDto>> create(
            @PathVariable UUID classroomId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatePostRequest request) {
        PostDto dto = postService.create(classroomId, principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @Operation(summary = "Update post (author or teacher) [MEMBER]")
    @PutMapping("/{postId}")
    @PreAuthorize("@classroomSecurity.isMember(#classroomId, authentication)")
    public ResponseEntity<ApiResponse<PostDto>> update(
            @PathVariable UUID classroomId,
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdatePostRequest request) {
        PostDto dto = postService.update(classroomId, postId, principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @Operation(summary = "Delete post (author or teacher) [MEMBER]")
    @DeleteMapping("/{postId}")
    @PreAuthorize("@classroomSecurity.isMember(#classroomId, authentication)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID classroomId,
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        postService.delete(classroomId, postId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Upload attachments to post (author or teacher) [MEMBER]")
    @PostMapping("/{postId}/attachments")
    @PreAuthorize("@classroomSecurity.isMember(#classroomId, authentication)")
    public ResponseEntity<ApiResponse<List<PostDto.AttachmentDto>>> addAttachments(
            @PathVariable UUID classroomId,
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("files") List<MultipartFile> files) {
        List<PostDto.AttachmentDto> dtos = postService.addAttachments(classroomId, postId, principal.userId(), files);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dtos));
    }

    @Operation(summary = "Delete attachment (author or teacher) [MEMBER]")
    @DeleteMapping("/{postId}/attachments/{attachmentId}")
    @PreAuthorize("@classroomSecurity.isMember(#classroomId, authentication)")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable UUID classroomId,
            @PathVariable UUID postId,
            @PathVariable UUID attachmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        postService.deleteAttachment(classroomId, postId, attachmentId, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
