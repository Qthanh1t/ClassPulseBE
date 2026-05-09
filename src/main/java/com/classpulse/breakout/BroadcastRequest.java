package com.classpulse.breakout;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BroadcastRequest {

    @NotBlank
    private String content;
}
