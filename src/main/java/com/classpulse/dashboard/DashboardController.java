package com.classpulse.dashboard;

import com.classpulse.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Get session dashboard [OWNER]")
    @GetMapping
    @PreAuthorize("@sessionSecurity.isOwner(#sessionId, authentication)")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDashboard(sessionId)));
    }
}
