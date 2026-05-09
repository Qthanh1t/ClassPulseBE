package com.classpulse.breakout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreateRoomRequest {

    @NotBlank
    private String name;

    private String task;

    @NotEmpty
    private List<UUID> studentIds;
}
