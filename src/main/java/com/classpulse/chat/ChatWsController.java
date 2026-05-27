package com.classpulse.chat;

import com.classpulse.session.SessionBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
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

        // Broadcast the DTO directly — Jackson serializes it to { id, sender: { id, name, role, avatarColor }, content, sentAt }
        // which matches the ChatMessageDto interface expected by the frontend dtoToChat() function.
        if (dto.breakoutRoomId() != null) {
            broadcastService.broadcastToRoom(sessionId, dto.breakoutRoomId(), "chat_message", dto);
        } else {
            broadcastService.broadcastToSession(sessionId, "chat_message", dto);
        }

        log.info("Chat message sent in session={} by sender={}", sessionId, dto.sender().id());
    }
}
