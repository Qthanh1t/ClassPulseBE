package com.classpulse.question;

import com.classpulse.common.response.ApiResponse;
import com.classpulse.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Student Answers")
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/questions/{questionId}/answers")
@RequiredArgsConstructor
public class StudentAnswerController {

    private final StudentAnswerService studentAnswerService;

    @Operation(summary = "Submit answer [STUDENT]")
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<StudentAnswerDto>> submit(
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId,
            Authentication authentication,
            @Valid @RequestBody SubmitAnswerRequest request) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        StudentAnswerDto dto = studentAnswerService.submit(sessionId, questionId, principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @Operation(summary = "Get answers for question [AUTH] — teacher sees all, student sees own")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<StudentAnswerDto>>> getAnswers(
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(studentAnswerService.getAnswers(sessionId, questionId, authentication)));
    }
}
