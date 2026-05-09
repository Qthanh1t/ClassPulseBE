package com.classpulse.breakout;

import com.classpulse.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Breakout Rooms")
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/breakouts")
@RequiredArgsConstructor
public class BreakoutController {

    private final BreakoutService breakoutService;

    @Operation(summary = "Create breakout session [OWNER]")
    @PostMapping
    @PreAuthorize("@sessionSecurity.isOwner(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<BreakoutSessionDto>> create(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CreateBreakoutRequest request) {
        BreakoutSessionDto dto = breakoutService.create(sessionId, request);
        // Broadcast breakout_started wired in M13
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
        // Broadcast breakout_ended wired in M13
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Broadcast message to all rooms [OWNER]")
    @PostMapping("/{breakoutId}/broadcast")
    @PreAuthorize("@sessionSecurity.isOwner(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<BroadcastResponse>> broadcast(
            @PathVariable UUID sessionId,
            @PathVariable UUID breakoutId,
            @Valid @RequestBody BroadcastRequest request) {
        // Broadcast broadcast_message wired in M13
        return ResponseEntity.ok(ApiResponse.ok(breakoutService.broadcast(sessionId, breakoutId, request)));
    }

    @Operation(summary = "Teacher join breakout room [OWNER]")
    @PostMapping("/{breakoutId}/rooms/{roomId}/join")
    @PreAuthorize("@sessionSecurity.isOwner(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<JoinRoomResponse>> joinRoom(
            @PathVariable UUID sessionId,
            @PathVariable UUID breakoutId,
            @PathVariable UUID roomId) {
        // Broadcast teacher_joined_room wired in M13
        return ResponseEntity.ok(ApiResponse.ok(breakoutService.joinRoom(sessionId, breakoutId, roomId)));
    }

    @Operation(summary = "Teacher leave breakout room [OWNER]")
    @PostMapping("/{breakoutId}/rooms/{roomId}/leave")
    @PreAuthorize("@sessionSecurity.isOwner(#sessionId, authentication)")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable UUID sessionId,
            @PathVariable UUID breakoutId,
            @PathVariable UUID roomId) {
        breakoutService.leaveRoom(sessionId, breakoutId, roomId);
        // Broadcast teacher_left_room wired in M13
        return ResponseEntity.noContent().build();
    }
}
