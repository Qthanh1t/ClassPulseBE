package com.classpulse.classroom;

import com.classpulse.common.response.ApiResponse;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Classrooms")
@RestController
@RequestMapping("/api/v1/classrooms")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService classroomService;

    @Operation(summary = "List classrooms for current user [AUTH]")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ClassroomDto>>> listForUser(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ClassroomDto> classrooms = classroomService.listForUser(principal.userId(), principal.role());
        return ResponseEntity.ok(ApiResponse.ok(classrooms));
    }

    @Operation(summary = "Create classroom [TEACHER]")
    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<ClassroomDto>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateClassroomRequest request) {
        ClassroomDto dto = classroomService.create(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @Operation(summary = "Get classroom by ID [MEMBER]")
    @GetMapping("/{classroomId}")
    @PreAuthorize("@classroomSecurity.isMember(#classroomId, authentication)")
    public ResponseEntity<ApiResponse<ClassroomDto>> getById(@PathVariable UUID classroomId) {
        return ResponseEntity.ok(ApiResponse.ok(classroomService.getById(classroomId)));
    }

    @Operation(summary = "Update classroom [OWNER]")
    @PutMapping("/{classroomId}")
    @PreAuthorize("@classroomSecurity.isOwner(#classroomId, authentication)")
    public ResponseEntity<ApiResponse<ClassroomDto>> update(
            @PathVariable UUID classroomId,
            @Valid @RequestBody UpdateClassroomRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(classroomService.update(classroomId, request)));
    }

    @Operation(summary = "Archive classroom (soft delete) [OWNER]")
    @DeleteMapping("/{classroomId}")
    @PreAuthorize("@classroomSecurity.isOwner(#classroomId, authentication)")
    public ResponseEntity<Void> archive(@PathVariable UUID classroomId) {
        classroomService.archive(classroomId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Join classroom by code [STUDENT]")
    @PostMapping("/join")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<JoinResponse>> join(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody JoinClassroomRequest request) {
        JoinResponse response = classroomService.join(principal.userId(), request.getJoinCode());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "List classroom members [MEMBER]")
    @GetMapping("/{classroomId}/members")
    @PreAuthorize("@classroomSecurity.isMember(#classroomId, authentication)")
    public ResponseEntity<ApiResponse<List<MemberDto>>> listMembers(@PathVariable UUID classroomId) {
        List<MemberDto> members = classroomService.listMembers(classroomId);
        return ResponseEntity.ok(ApiResponse.ok(members));
    }

    @Operation(summary = "Kick member from classroom [OWNER]")
    @DeleteMapping("/{classroomId}/members/{studentId}")
    @PreAuthorize("@classroomSecurity.isOwner(#classroomId, authentication)")
    public ResponseEntity<Void> kickMember(
            @PathVariable UUID classroomId,
            @PathVariable UUID studentId) {
        classroomService.kickMember(classroomId, studentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Regenerate join code [OWNER]")
    @PostMapping("/{classroomId}/join-code/regenerate")
    @PreAuthorize("@classroomSecurity.isOwner(#classroomId, authentication)")
    public ResponseEntity<ApiResponse<Map<String, String>>> regenerateCode(@PathVariable UUID classroomId) {
        String newCode = classroomService.regenerateCode(classroomId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("joinCode", newCode)));
    }
}
