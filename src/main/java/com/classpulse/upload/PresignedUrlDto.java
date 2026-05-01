package com.classpulse.upload;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class PresignedUrlDto {

    private String fileName;
    private String fileKey;
    private String uploadUrl;
    private Instant expiresAt;
}
