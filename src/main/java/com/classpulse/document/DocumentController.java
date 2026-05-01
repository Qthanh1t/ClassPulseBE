package com.classpulse.document;

import com.classpulse.common.response.ApiResponse;
import com.classpulse.common.response.PageMeta;
import com.classpulse.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Documents")
@RestController
@RequestMapping("/api/v1/classrooms/{classroomId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "List classroom documents — post attachments + direct uploads [MEMBER]")
    @GetMapping
    @PreAuthorize("@classroomSecurity.isMember(#classroomId, authentication)")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> list(
            @PathVariable UUID classroomId,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Map.Entry<List<DocumentDto>, PageMeta> result = documentService.list(classroomId, source, page, limit);
        return ResponseEntity.ok(ApiResponse.ok(result.getKey(), result.getValue()));
    }

    @Operation(summary = "Upload documents directly to classroom [OWNER]")
    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("@classroomSecurity.isOwner(#classroomId, authentication)")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> upload(
            @PathVariable UUID classroomId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("files") List<MultipartFile> files) {
        List<DocumentDto> dtos = documentService.upload(classroomId, principal.userId(), files);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dtos));
    }

    @Operation(summary = "Delete a direct-upload document [OWNER]")
    @DeleteMapping("/{documentId}")
    @PreAuthorize("@classroomSecurity.isOwner(#classroomId, authentication)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID classroomId,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        documentService.delete(classroomId, documentId, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
