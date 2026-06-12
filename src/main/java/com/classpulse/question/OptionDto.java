package com.classpulse.question;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

// isCorrect là Boolean (nullable): null = ẩn đáp án (payload gửi cho học sinh) — Jackson bỏ field
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OptionDto(UUID id, String label, String text, Boolean isCorrect, int order) {

    static OptionDto from(QuestionOption option) {
        return new OptionDto(
                option.getId(),
                option.getLabel(),
                option.getText(),
                option.isCorrect(),
                option.getOptionOrder()
        );
    }

    // Bản sao không lộ đáp án — dùng cho broadcast question_started và REST list phía học sinh
    OptionDto withoutCorrect() {
        return new OptionDto(id, label, text, null, order);
    }
}
