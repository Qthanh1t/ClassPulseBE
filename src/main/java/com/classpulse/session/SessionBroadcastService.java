package com.classpulse.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    /** Broadcast to all participants of a session: /topic/session/{sessionId} */
    public void broadcastToSession(UUID sessionId, String type, Object payload) {
        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                Map.of("type", type, "payload", payload));
        log.debug("Broadcast type={} to session={}", type, sessionId);
    }

    /** Unicast to a single user: /user/{userId}/queue/private */
    public void sendToUser(UUID userId, String type, Object payload) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(), "/queue/private",
                Map.of("type", type, "payload", payload));
        log.debug("Sent type={} to user={}", type, userId);
    }

    /** Broadcast to a specific breakout room: /topic/session/{sessionId}/room/{roomId} */
    public void broadcastToRoom(UUID sessionId, UUID roomId, String type, Object payload) {
        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId + "/room/" + roomId,
                Map.of("type", type, "payload", payload));
        log.debug("Broadcast type={} to session={} room={}", type, sessionId, roomId);
    }
}
