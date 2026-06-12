package com.classpulse.question;

import com.classpulse.common.response.ApiResponse;
import com.classpulse.session.SessionBroadcastService;
import com.classpulse.session.SessionSecurityBean;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Questions")
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final SessionBroadcastService broadcastService;
    private final SessionSecurityBean sessionSecurity;

    @Operation(summary = "List questions in session [AUTH]")
    @GetMapping("/questions")
    public ResponseEntity<ApiResponse<List<QuestionDto>>> list(
            @PathVariable UUID sessionId, Authentication authentication) {
        List<QuestionDto> questions = questionService.list(sessionId);
        // Học sinh không được thấy isCorrect (chống lộ đáp án khi câu hỏi đang chạy)
        if (!sessionSecurity.isOwner(sessionId, authentication)) {
            questions = questions.stream().map(QuestionDto::sanitized).toList();
        }
        return ResponseEntity.ok(ApiResponse.ok(questions));
    }

    @Operation(summary = "Create question [OWNER]")
    @PostMapping("/questions")
    @PreAuthorize("@sessionSecurity.isOwner(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<QuestionDto>> create(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CreateQuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(questionService.create(sessionId, request)));
    }

    @Operation(summary = "Start question [OWNER]")
    @PostMapping("/questions/{questionId}/start")
    @PreAuthorize("@sessionSecurity.isOwner(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<QuestionStartResponse>> start(
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId) {
        QuestionStartResponse resp = questionService.start(sessionId, questionId);
        QuestionDto question = questionService.get(sessionId, questionId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("questionId", questionId);
        payload.put("type", question.type());
        payload.put("content", question.content());
        if (question.options() != null && !question.options().isEmpty()) {
            // Broadcast tới cả lớp (topic chung) — phải ẩn isCorrect, đáp án đúng chỉ
            // được tiết lộ trong question_ended (correctOptionIds)
            payload.put("options", question.options().stream().map(OptionDto::withoutCorrect).toList());
        }
        if (resp.endsAt() != null) {
            payload.put("endsAt", resp.endsAt());
        }
        // Server timestamp so clients can correct local clock skew when counting down to endsAt
        payload.put("serverNow", java.time.Instant.now());
        broadcastService.broadcastToSession(sessionId, "question_started", payload);

        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    @Operation(summary = "End question [OWNER]")
    @PostMapping("/questions/{questionId}/end")
    @PreAuthorize("@sessionSecurity.isOwner(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<QuestionEndResponse>> end(
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId) {
        QuestionEndResponse resp = questionService.end(sessionId, questionId);
        // Kèm đáp án đúng để học sinh xem lại kết quả sau khi câu hỏi kết thúc
        List<UUID> correctOptionIds = questionService.getCorrectOptionIds(sessionId, questionId);
        broadcastService.broadcastToSession(sessionId, "question_ended",
                Map.of("questionId", questionId, "correctOptionIds", correctOptionIds));
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    @Operation(summary = "Get question stats [OWNER]")
    @GetMapping("/questions/{questionId}/stats")
    @PreAuthorize("@sessionSecurity.isOwner(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<QuestionStatsDto>> getStats(
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId) {
        return ResponseEntity.ok(ApiResponse.ok(questionService.getStats(sessionId, questionId)));
    }
}
