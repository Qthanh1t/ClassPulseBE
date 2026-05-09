package com.classpulse.breakout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateBreakoutRequest {

    @Valid
    @NotEmpty
    private List<CreateRoomRequest> rooms;
}
