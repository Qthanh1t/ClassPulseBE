package com.classpulse.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "avatarColor must be a valid hex color (e.g. #6366f1)")
    private String avatarColor;
}
