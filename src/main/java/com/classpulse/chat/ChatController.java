package com.classpulse.chat;

import com.classpulse.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Chat")
@Validated
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "Get chat history (cursor-based) [PARTICIPANT]")
    @GetMapping
    @PreAuthorize("@sessionSecurity.isParticipant(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<List<ChatMessageDto>>> getHistory(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) UUID before) {
        Map.Entry<List<ChatMessageDto>, ChatCursorMeta> result =
                chatService.getHistory(sessionId, before, limit);
        return ResponseEntity.ok(ApiResponse.ok(result.getKey(), result.getValue()));
    }
}
