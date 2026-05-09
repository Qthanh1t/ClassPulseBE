package com.classpulse.session;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class WebRtcIceRequest {

    @NotNull
    private UUID targetId;

    /** RTCIceCandidateInit object forwarded as-is to the target peer. */
    @NotNull
    private Object candidate;
}
