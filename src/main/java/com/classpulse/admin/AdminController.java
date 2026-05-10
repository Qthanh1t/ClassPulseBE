package com.classpulse.admin;

import com.classpulse.classroom.ClassroomDto;
import com.classpulse.common.response.ApiResponse;
import com.classpulse.common.response.PageMeta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "Get system stats [ADMIN]")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminStatsDto>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getStats()));
    }

    @Operation(summary = "List all classrooms [ADMIN]")
    @GetMapping("/classrooms")
    public ResponseEntity<ApiResponse<List<ClassroomDto>>> listClassrooms(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Map.Entry<List<ClassroomDto>, PageMeta> result = adminService.listClassrooms(search, page, limit);
        return ResponseEntity.ok(ApiResponse.ok(result.getKey(), result.getValue()));
    }
}
