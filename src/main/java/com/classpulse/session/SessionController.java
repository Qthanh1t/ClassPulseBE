package com.classpulse.session;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Sessions")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @Operation(summary = "Start session [OWNER]")
    @PostMapping("/classrooms/{classroomId}/sessions")
    @PreAuthorize("@classroomSecurity.isOwner(#classroomId, authentication)")
    public ResponseEntity<ApiResponse<SessionDto>> start(
            @PathVariable UUID classroomId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) CreateSessionRequest request) {
        SessionDto dto = sessionService.start(classroomId, principal.userId(),
                request != null ? request : new CreateSessionRequest());
        // Broadcast session_started wired in M13 (T083)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @Operation(summary = "List sessions in classroom [MEMBER]")
    @GetMapping("/classrooms/{classroomId}/sessions")
    @PreAuthorize("@classroomSecurity.isMember(#classroomId, authentication)")
    public ResponseEntity<ApiResponse<List<SessionDto>>> listByClassroom(
            @PathVariable UUID classroomId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Map.Entry<List<SessionDto>, PageMeta> result = sessionService.listByClassroom(classroomId, page, limit);
        return ResponseEntity.ok(ApiResponse.ok(result.getKey(), result.getValue()));
    }

    @Operation(summary = "Get session detail [AUTH]")
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<SessionDetailDto>> getDetail(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(sessionService.getDetail(sessionId)));
    }

    @Operation(summary = "End session [OWNER]")
    @PostMapping("/sessions/{sessionId}/end")
    @PreAuthorize("@sessionSecurity.isOwner(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<SessionEndResponse>> end(@PathVariable UUID sessionId) {
        SessionEndResponse response = sessionService.end(sessionId);
        // Broadcast session_ended wired in M13 (T083)
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Join session [STUDENT]")
    @PostMapping("/sessions/{sessionId}/join")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<JoinSessionResponse>> join(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(sessionService.join(sessionId, principal.userId())));
    }

    @Operation(summary = "Leave session [STUDENT]")
    @PostMapping("/sessions/{sessionId}/leave")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> leave(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        sessionService.leave(sessionId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get presence list [PARTICIPANT]")
    @GetMapping("/sessions/{sessionId}/presence")
    @PreAuthorize("@sessionSecurity.isParticipant(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<List<PresenceDto>>> getPresence(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(sessionService.getPresence(sessionId)));
    }
}
