package com.classpulse.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CameraStateRequest {
    @JsonProperty("isCameraOff")
    private boolean isCameraOff;
}
