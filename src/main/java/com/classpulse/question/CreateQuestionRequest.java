package com.classpulse.question;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateQuestionRequest {

    @NotNull
    private QuestionType type;

    @NotBlank
    private String content;

    @Positive
    private Integer timerSeconds;

    @Valid
    private List<CreateOptionRequest> options;
}
