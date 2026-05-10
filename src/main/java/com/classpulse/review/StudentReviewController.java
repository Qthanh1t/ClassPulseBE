package com.classpulse.review;

import com.classpulse.common.response.ApiResponse;
import com.classpulse.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Review")
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/review")
@RequiredArgsConstructor
public class StudentReviewController {

    private final StudentReviewService reviewService;

    @Operation(summary = "Get personal session review [STUDENT + PARTICIPANT]")
    @GetMapping
    @PreAuthorize("hasRole('STUDENT') and @sessionSecurity.isParticipant(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<ReviewResponse>> getReview(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getReview(sessionId, principal.userId())));
    }
}
