package com.classpulse.session;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
public class HeartbeatWsController {

    /**
     * No-op handler. Receiving this message resets the STOMP idle timer and
     * prevents the server from closing an otherwise silent connection.
     * Client sends every 25 s: /app/session/{sessionId}/heartbeat
     */
    @MessageMapping("/session/{sessionId}/heartbeat")
    public void handleHeartbeat(
            @DestinationVariable UUID sessionId,
            Principal principal) {
        // intentionally empty — transport-level keep-alive
    }
}
