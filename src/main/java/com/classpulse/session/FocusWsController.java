package com.classpulse.session;

import com.classpulse.common.security.StompPrincipal;
import com.classpulse.user.Role;
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
public class FocusWsController {

    private final SessionBroadcastService broadcastService;

    @MessageMapping("/session/{sessionId}/focus")
    public void handleFocusStudent(
            @DestinationVariable UUID sessionId,
            @Payload FocusStudentRequest request,
            Principal principal) {
        if (!(principal instanceof StompPrincipal sp)) return;
        if (sp.role() != Role.TEACHER) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("focusedStudentId", request.getStudentId()); // null = unfocus

        broadcastService.broadcastToSession(sessionId, "focus_changed", payload);

        log.debug("Teacher {} set focus={} in session {}", sp.userId(), request.getStudentId(), sessionId);
    }
}
