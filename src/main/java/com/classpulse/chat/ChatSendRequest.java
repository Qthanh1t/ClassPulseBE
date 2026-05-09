package com.classpulse.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ChatSendRequest {

    @NotBlank
    @Size(max = 2000)
    private String content;

    private UUID breakoutRoomId;
}
