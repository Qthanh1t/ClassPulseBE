package com.classpulse.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class WebRtcSdpRequest {

    @NotNull
    private UUID targetId;

    @NotBlank
    private String sdp;
}
