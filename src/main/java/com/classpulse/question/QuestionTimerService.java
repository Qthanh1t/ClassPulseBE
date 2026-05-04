package com.classpulse.question;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionTimerService {

    private final QuestionRepository questionRepository;
    private final TransactionTemplate transactionTemplate;
    private final StringRedisTemplate redisTemplate;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();

    public void startTimer(UUID questionId, int timerSeconds, UUID sessionId) {
        ScheduledFuture<?> future = scheduler.schedule(
                () -> {
                    autoEndQuestion(questionId, sessionId);
                    activeTimers.remove(questionId);
                    // Broadcast question_ended — wire SessionBroadcastService in M13 (T083)
                },
                timerSeconds, TimeUnit.SECONDS);
        activeTimers.put(questionId, future);
        log.info("Timer started for question {} ({}s)", questionId, timerSeconds);
    }

    public void cancelTimer(UUID questionId) {
        ScheduledFuture<?> future = activeTimers.remove(questionId);
        if (future != null) {
            future.cancel(false);
            log.info("Timer cancelled for question {}", questionId);
        }
    }

    // Called by timer thread — uses TransactionTemplate because @Transactional proxy is bypassed
    private void autoEndQuestion(UUID questionId, UUID sessionId) {
        transactionTemplate.execute(tx -> {
            questionRepository.findById(questionId).ifPresent(q -> {
                if (q.getStatus() == QuestionStatus.running) {
                    q.setStatus(QuestionStatus.ended);
                    q.setEndedAt(Instant.now());
                    questionRepository.save(q);
                }
            });
            return null;
        });
        redisTemplate.delete("session:" + sessionId + ":active_question");
        log.info("Auto-ended question {} (timer expired)", questionId);
    }

    // R02 mitigation — recover running questions after server restart
    @EventListener(ApplicationStartedEvent.class)
    public void recoverActiveTimers() {
        List<Question> running = questionRepository.findAllRunning();
        if (running.isEmpty()) return;

        log.info("Recovering {} running question(s) after startup", running.size());
        Instant now = Instant.now();

        for (Question q : running) {
            UUID sessionId = q.getSession().getId();
            if (q.getEndsAt() == null) {
                // No timer — leave as-is, teacher ends manually
                continue;
            }
            if (q.getEndsAt().isBefore(now)) {
                // Timer already expired — end immediately
                autoEndQuestion(q.getId(), sessionId);
            } else {
                // Re-schedule remaining time
                long remainingMs = Duration.between(now, q.getEndsAt()).toMillis();
                ScheduledFuture<?> future = scheduler.schedule(
                        () -> {
                            autoEndQuestion(q.getId(), sessionId);
                            activeTimers.remove(q.getId());
                        },
                        remainingMs, TimeUnit.MILLISECONDS);
                activeTimers.put(q.getId(), future);
                log.info("Rescheduled timer for question {} ({}ms remaining)", q.getId(), remainingMs);
            }
        }
    }
}
