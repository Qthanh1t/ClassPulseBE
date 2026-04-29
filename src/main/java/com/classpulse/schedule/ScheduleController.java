package com.classpulse.schedule;

import com.classpulse.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Schedules")
@RestController
@RequestMapping("/api/v1/classrooms/{classroomId}/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(summary = "List schedules in classroom [MEMBER]")
    @GetMapping
    @PreAuthorize("@classroomSecurity.isMember(#classroomId, authentication)")
    public ResponseEntity<ApiResponse<List<ScheduleDto>>> list(
            @PathVariable UUID classroomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<ScheduleDto> dtos = scheduleService.list(classroomId, from, to);
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @Operation(summary = "Create schedule [OWNER]")
    @PostMapping
    @PreAuthorize("@classroomSecurity.isOwner(#classroomId, authentication)")
    public ResponseEntity<ApiResponse<ScheduleDto>> create(
            @PathVariable UUID classroomId,
            @Valid @RequestBody CreateScheduleRequest request) {
        ScheduleDto dto = scheduleService.create(classroomId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @Operation(summary = "Update schedule [OWNER]")
    @PutMapping("/{scheduleId}")
    @PreAuthorize("@classroomSecurity.isOwner(#classroomId, authentication)")
    public ResponseEntity<ApiResponse<ScheduleDto>> update(
            @PathVariable UUID classroomId,
            @PathVariable UUID scheduleId,
            @Valid @RequestBody UpdateScheduleRequest request) {
        ScheduleDto dto = scheduleService.update(classroomId, scheduleId, request);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @Operation(summary = "Delete schedule [OWNER]")
    @DeleteMapping("/{scheduleId}")
    @PreAuthorize("@classroomSecurity.isOwner(#classroomId, authentication)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID classroomId,
            @PathVariable UUID scheduleId) {
        scheduleService.delete(classroomId, scheduleId);
        return ResponseEntity.noContent().build();
    }
}
