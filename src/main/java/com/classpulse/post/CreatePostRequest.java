package com.classpulse.post;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class CreatePostRequest {

    @NotBlank(message = "Content is required")
    private String content;
}
