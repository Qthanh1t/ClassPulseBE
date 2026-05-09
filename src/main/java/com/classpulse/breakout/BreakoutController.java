package com.classpulse.breakout;

import com.classpulse.common.response.ApiResponse;
import com.classpulse.session.SessionBroadcastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Breakout Rooms")
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/breakouts")
@RequiredArgsConstructor
public class BreakoutController {

    private final BreakoutService breakoutService;
    private final SessionBroadcastService broadcastService;

    @Operation(summary = "Create breakout session [OWNER]")
    @PostMapping
    @PreAuthorize("@sessionSecurity.isOwner(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<BreakoutSessionDto>> create(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CreateBreakoutRequest request) {
        BreakoutSessionDto dto = breakoutService.create(sessionId, request);
        broadcastService.broadcastToSession(sessionId, "breakout_started", Map.of(
                "breakoutSessionId", dto.breakoutSessionId(),
                "rooms", dto.rooms().stream().map(r -> {
                    Map<String, Object> room = new HashMap<>();
                    room.put("id", r.id());
                    room.put("name", r.name());
                    if (r.task() != null) room.put("task", r.task());
                    room.put("studentIds", r.students().stream().map(BreakoutSessionDto.StudentInfo::id).toList());
                    return room;
                }).toList()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @Operation(summary = "Get active breakout [AUTH]")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<BreakoutSessionDto>> getActive(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(breakoutService.getActive(sessionId)));
    }

    @Operation(summary = "End breakout session [OWNER]")
    @PostMapping("/{breakoutId}/end")
    @PreAuthorize("@sessionSecurity.isOwner(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<BreakoutEndResponse>> end(
            @PathVariable UUID sessionId,
            @PathVariable UUID breakoutId) {
        BreakoutEndResponse response = breakoutService.end(sessionId, breakoutId);
        broadcastService.broadcastToSession(sessionId, "breakout_ended",
                Map.of("breakoutSessionId", breakoutId));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Broadcast message to all rooms [OWNER]")
    @PostMapping("/{breakoutId}/broadcast")
    @PreAuthorize("@sessionSecurity.isOwner(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<BroadcastResponse>> broadcast(
            @PathVariable UUID sessionId,
            @PathVariable UUID breakoutId,
            @Valid @RequestBody BroadcastRequest request) {
        BroadcastResponse response = breakoutService.broadcast(sessionId, breakoutId, request);
        broadcastService.broadcastToSession(sessionId, "broadcast_message", Map.of(
                "content", request.getContent(),
                "sentAt", response.sentAt()
        ));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Teacher join breakout room [OWNER]")
    @PostMapping("/{breakoutId}/rooms/{roomId}/join")
    @PreAuthorize("@sessionSecurity.isOwner(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<JoinRoomResponse>> joinRoom(
            @PathVariable UUID sessionId,
            @PathVariable UUID breakoutId,
            @PathVariable UUID roomId) {
        JoinRoomResponse response = breakoutService.joinRoom(sessionId, breakoutId, roomId);
        broadcastService.broadcastToRoom(sessionId, roomId, "teacher_joined_room", Map.of(
                "roomId", roomId,
                "roomName", response.roomName()
        ));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Teacher leave breakout room [OWNER]")
    @PostMapping("/{breakoutId}/rooms/{roomId}/leave")
    @PreAuthorize("@sessionSecurity.isOwner(#sessionId, authentication)")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable UUID sessionId,
            @PathVariable UUID breakoutId,
            @PathVariable UUID roomId) {
        breakoutService.leaveRoom(sessionId, breakoutId, roomId);
        broadcastService.broadcastToRoom(sessionId, roomId, "teacher_left_room",
                Map.of("roomId", roomId));
        return ResponseEntity.noContent().build();
    }
}
