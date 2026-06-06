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
    /** Relative public URL (/storage/{bucket}/{key}) to embed once the PUT completes. */
    private String url;
    private Instant expiresAt;
}
