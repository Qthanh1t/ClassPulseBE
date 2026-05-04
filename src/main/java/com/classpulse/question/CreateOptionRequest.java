package com.classpulse.question;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOptionRequest {

    @NotBlank
    @Size(max = 5)
    private String label;

    @NotBlank
    private String text;

    @NotNull
    private Boolean isCorrect;
}
