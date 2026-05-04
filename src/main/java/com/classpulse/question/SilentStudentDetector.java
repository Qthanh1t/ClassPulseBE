package com.classpulse.question;

import com.classpulse.session.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SilentStudentDetector {

    private final SessionRepository sessionRepository;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 10_000)
    public void detectSilentStudents() {
        List<UUID> activeSessionIds = sessionRepository.findActiveIds();

        for (UUID sessionId : activeSessionIds) {
            String activeQuestionId = redisTemplate.opsForValue()
                    .get("session:" + sessionId + ":active_question");
            if (activeQuestionId == null) continue;

            Set<String> present = redisTemplate.opsForSet()
                    .members("session:" + sessionId + ":presence");
            if (present == null || present.isEmpty()) continue;

            Set<String> answered = redisTemplate.opsForSet()
                    .members("session:" + sessionId + ":question:" + activeQuestionId + ":answered");

            Set<String> silent = new HashSet<>(present);
            if (answered != null) silent.removeAll(answered);

            if (!silent.isEmpty()) {
                log.debug("Session {}: {} silent student(s) on question {}", sessionId, silent.size(), activeQuestionId);
                // Send silent_alert to teacher — wire SessionBroadcastService in M13 (T083)
                // broadcastService.sendToTeacher(teacherId,
                //     Map.of("type", "silent_alert", "payload", Map.of("silentStudentIds", silent)));
            }
        }
    }
}
