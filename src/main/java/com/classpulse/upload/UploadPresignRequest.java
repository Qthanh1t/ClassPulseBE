package com.classpulse.upload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UploadPresignRequest {

    @NotNull
    @Size(min = 1, max = 10, message = "Must request between 1 and 10 files at once")
    @Valid
    private List<FileInfo> files;

    @NotBlank
    @Pattern(regexp = "post_attachment|classroom_document|avatar|question_attachment|answer_attachment",
             message = "purpose must be post_attachment, classroom_document, avatar, question_attachment, or answer_attachment")
    private String purpose;

    @Getter
    @Setter
    public static class FileInfo {

        @NotBlank
        @Size(max = 255)
        private String fileName;

        @NotBlank
        private String contentType;

        @Positive
        private long fileSizeBytes;
    }
}
