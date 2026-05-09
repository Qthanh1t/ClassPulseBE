package com.classpulse.session;

import com.classpulse.common.security.StompPrincipal;
import com.classpulse.user.Role;
import com.classpulse.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
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
public class RaiseHandWsController {

    private final RaisedHandRepository raisedHandRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final SessionBroadcastService broadcastService;
    private final StringRedisTemplate redisTemplate;

    @MessageMapping("/session/{sessionId}/raise-hand")
    public void handleRaiseHand(
            @DestinationVariable UUID sessionId,
            @Payload RaiseHandRequest request,
            Principal principal) {
        if (!(principal instanceof StompPrincipal sp)) return;
        if (sp.role() != Role.STUDENT) return;

        UUID studentId = sp.userId();
        String key = "session:" + sessionId + ":raised_hands";

        if (request.isRaised()) {
            redisTemplate.opsForSet().add(key, studentId.toString());
        } else {
            redisTemplate.opsForSet().remove(key, studentId.toString());
        }

        raisedHandRepository.save(RaisedHand.builder()
                .session(sessionRepository.getReferenceById(sessionId))
                .student(userRepository.getReferenceById(studentId))
                .raised(request.isRaised())
                .build());

        broadcastService.broadcastToSession(sessionId, "raise_hand_changed",
                Map.of("studentId", studentId, "raised", request.isRaised()));

        log.debug("Student {} {} hand in session {}",
                studentId, request.isRaised() ? "raised" : "lowered", sessionId);
    }
}
