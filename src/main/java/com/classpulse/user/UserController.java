package com.classpulse.user;

import com.classpulse.common.exception.BusinessException;
import com.classpulse.common.response.ApiResponse;
import com.classpulse.common.response.PageMeta;
import com.classpulse.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Users")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get current user profile [AUTH]")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMe(principal.userId())));
    }

    @Operation(summary = "Update current user profile [AUTH]")
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(principal.userId(), request)));
    }

    @Operation(summary = "Upload avatar [AUTH]")
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAvatar(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        String avatarUrl = userService.uploadAvatar(principal.userId(), file);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("avatarUrl", avatarUrl)));
    }

    @Operation(summary = "List all users [ADMIN]")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserDto>>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search) {
        Role roleFilter = parseRole(role);
        Page<UserDto> result = userService.listUsers(page, limit, roleFilter, search);
        return ResponseEntity.ok(ApiResponse.ok(result.getContent(), PageMeta.from(result)));
    }

    @Operation(summary = "Admin update user role/status [ADMIN]")
    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> adminUpdateUser(
            @PathVariable UUID userId,
            @RequestBody AdminUpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.adminUpdateUser(userId, request)));
    }

    private Role parseRole(String role) {
        if (role == null || role.isBlank()) return null;
        try {
            return Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_ROLE", "Invalid role: " + role);
        }
    }
}
