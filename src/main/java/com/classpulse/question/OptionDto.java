package com.classpulse.question;

import java.util.UUID;

public record OptionDto(UUID id, String label, String text, boolean isCorrect, int order) {

    static OptionDto from(QuestionOption option) {
        return new OptionDto(
                option.getId(),
                option.getLabel(),
                option.getText(),
                option.isCorrect(),
                option.getOptionOrder()
        );
    }
}
