package com.classpulse.common.security;

import com.classpulse.classroom.ClassroomSecurityBean;
import com.classpulse.session.SessionSecurityBean;
import com.classpulse.user.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final SessionSecurityBean sessionSecurity;
    private final ClassroomSecurityBean classroomSecurity;

    // /topic/session/{uuid}  hoặc  /topic/session/{uuid}/room/{uuid}
    private static final Pattern SESSION_TOPIC = Pattern.compile(
            "^/topic/session/([0-9a-fA-F-]{36})(?:/room/[0-9a-fA-F-]{36})?$");
    // /topic/classroom/{uuid}
    private static final Pattern CLASSROOM_TOPIC = Pattern.compile(
            "^/topic/classroom/([0-9a-fA-F-]{36})$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();
        if (command != StompCommand.SEND && command != StompCommand.SUBSCRIBE) {
            return message;
        }

        Principal user = accessor.getUser();
        if (user == null) {
            log.warn("STOMP {} rejected: no authenticated principal on destination={}",
                    command, accessor.getDestination());
            throw new AccessDeniedException("Not authenticated");
        }

        // Gác quyền ĐỌC: chỉ participant của phiên / thành viên lớp mới SUBSCRIBE được topic tương ứng.
        // (SEND đi qua /app/** và được chính @MessageMapping controller kiểm tra theo vai.)
        if (command == StompCommand.SUBSCRIBE && user instanceof StompPrincipal sp) {
            String dest = accessor.getDestination();
            if (dest != null) {
                Matcher sm = SESSION_TOPIC.matcher(dest);
                if (sm.matches()) {
                    UUID sessionId = UUID.fromString(sm.group(1));
                    if (!sessionSecurity.isParticipant(sessionId, sp.userId(), sp.role() == Role.TEACHER)) {
                        log.warn("STOMP SUBSCRIBE rejected: user={} not participant of session={}", sp.userId(), sessionId);
                        throw new AccessDeniedException("Not a participant of this session");
                    }
                } else {
                    Matcher cm = CLASSROOM_TOPIC.matcher(dest);
                    if (cm.matches()) {
                        UUID classroomId = UUID.fromString(cm.group(1));
                        if (!classroomSecurity.isMember(classroomId, sp.userId())) {
                            log.warn("STOMP SUBSCRIBE rejected: user={} not member of classroom={}", sp.userId(), classroomId);
                            throw new AccessDeniedException("Not a member of this classroom");
                        }
                    }
                }
            }
        }

        return message;
    }
}
