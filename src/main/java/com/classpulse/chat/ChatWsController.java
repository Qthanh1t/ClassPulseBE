package com.classpulse.chat;

import com.classpulse.session.SessionBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWsController {

    private final ChatService chatService;
    private final SessionBroadcastService broadcastService;

    @MessageMapping("/session/{sessionId}/chat")
    public void handleChatSend(
            @DestinationVariable UUID sessionId,
            @Payload ChatSendRequest request,
            Principal principal) {
        ChatMessageDto dto = chatService.send(sessionId, principal, request);

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", dto.id());
        payload.put("senderId", dto.sender().id());
        payload.put("senderName", dto.sender().name());
        payload.put("senderRole", dto.sender().role());
        payload.put("avatarColor", dto.sender().avatarColor());
        payload.put("content", dto.content());
        payload.put("breakoutRoomId", dto.breakoutRoomId());
        payload.put("sentAt", dto.sentAt());

        if (dto.breakoutRoomId() != null) {
            broadcastService.broadcastToRoom(sessionId, dto.breakoutRoomId(), "chat_message", payload);
        } else {
            broadcastService.broadcastToSession(sessionId, "chat_message", payload);
        }

        log.info("Chat message sent in session={} by sender={}", sessionId, dto.sender().id());
    }
}
