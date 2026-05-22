package com.classpulse.session;

import com.classpulse.common.security.StompPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CameraStateWsController {

    private final SessionBroadcastService broadcastService;

    @MessageMapping("/session/{sessionId}/camera-state")
    public void handleCameraState(
            @DestinationVariable UUID sessionId,
            @Payload CameraStateRequest request,
            Principal principal) {
        if (!(principal instanceof StompPrincipal sp)) return;

        broadcastService.broadcastToSession(sessionId, "camera_state_changed",
                Map.of("fromId", sp.userId(), "isCameraOff", request.isCameraOff()));

        log.debug("User {} camera {} in session {}", sp.userId(),
                request.isCameraOff() ? "off" : "on", sessionId);
    }
}
