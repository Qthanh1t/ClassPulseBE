package com.classpulse.upload;

import com.classpulse.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Upload")
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @Operation(summary = "Get presigned PUT URLs for direct MinIO upload [AUTH]")
    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<List<PresignedUrlDto>>> presign(
            @Valid @RequestBody UploadPresignRequest request) {
        List<PresignedUrlDto> dtos = uploadService.presign(request);
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }
}
