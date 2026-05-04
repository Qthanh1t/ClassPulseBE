package com.classpulse.question;

import com.classpulse.common.exception.BusinessException;
import com.classpulse.common.exception.ConflictException;
import com.classpulse.common.exception.NotFoundException;
import com.classpulse.session.Session;
import com.classpulse.session.SessionPresenceRepository;
import com.classpulse.session.SessionRepository;
import com.classpulse.session.SessionStatus;
import com.classpulse.user.User;
import com.classpulse.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final SessionRepository sessionRepository;
    private final SessionPresenceRepository sessionPresenceRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final UserRepository userRepository;
    private final QuestionTimerService questionTimerService;
    private final StringRedisTemplate redisTemplate;

    // T068 — list
    @Transactional(readOnly = true)
    public List<QuestionDto> list(UUID sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new NotFoundException("Session not found");
        }
        return questionRepository.findBySessionId(sessionId).stream()
                .map(QuestionDto::from)
                .toList();
    }

    // T068 — create
    @Transactional
    public QuestionDto create(UUID sessionId, CreateQuestionRequest request) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        if (request.getType() != QuestionType.essay) {
            if (request.getOptions() == null || request.getOptions().isEmpty()) {
                throw new BusinessException("OPTIONS_REQUIRED", "Options are required for single/multiple choice questions");
            }
            boolean hasCorrect = request.getOptions().stream().anyMatch(o -> Boolean.TRUE.equals(o.getIsCorrect()));
            if (!hasCorrect) {
                throw new BusinessException("NO_CORRECT_OPTION", "At least one correct option is required");
            }
        }

        short order = (short) (questionRepository.countBySession_Id(sessionId) + 1);

        Question question = Question.builder()
                .session(session)
                .questionOrder(order)
                .type(request.getType())
                .content(request.getContent())
                .timerSeconds(request.getTimerSeconds())
                .status(QuestionStatus.draft)
                .build();

        if (request.getOptions() != null) {
            List<QuestionOption> options = new ArrayList<>();
            for (int i = 0; i < request.getOptions().size(); i++) {
                CreateOptionRequest opt = request.getOptions().get(i);
                options.add(QuestionOption.builder()
                        .question(question)
                        .label(opt.getLabel())
                        .text(opt.getText())
                        .correct(Boolean.TRUE.equals(opt.getIsCorrect()))
                        .optionOrder((short) (i + 1))
                        .build());
            }
            question.setOptions(options);
        }

        questionRepository.save(question);
        log.info("Created question {} (type={}, order={}) in session {}", question.getId(), request.getType(), order, sessionId);
        return QuestionDto.from(question);
    }

    // T068 — start (draft → running)
    @Transactional
    public QuestionStartResponse start(UUID sessionId, UUID questionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        if (session.getStatus() != SessionStatus.active) {
            throw new BusinessException("SESSION_NOT_ACTIVE", "Session is not active");
        }

        questionRepository.findRunningBySessionId(sessionId).ifPresent(q -> {
            throw new ConflictException("QUESTION_ALREADY_RUNNING", "Another question is already running in this session");
        });

        Question question = questionRepository.findByIdAndSession_Id(questionId, sessionId)
                .orElseThrow(() -> new NotFoundException("Question not found"));

        if (question.getStatus() != QuestionStatus.draft) {
            throw new BusinessException("QUESTION_NOT_DRAFT", "Only draft questions can be started");
        }

        Instant now = Instant.now();
        Instant endsAt = question.getTimerSeconds() != null
                ? now.plusSeconds(question.getTimerSeconds())
                : null;

        question.setStatus(QuestionStatus.running);
        question.setStartedAt(now);
        question.setEndsAt(endsAt);
        questionRepository.save(question);

        // Track active question in Redis (TTL 5min)
        redisTemplate.opsForValue().set(
                "session:" + sessionId + ":active_question",
                questionId.toString(),
                Duration.ofMinutes(5));

        if (question.getTimerSeconds() != null) {
            questionTimerService.startTimer(questionId, question.getTimerSeconds(), sessionId);
        }

        log.info("Started question {} in session {}", questionId, sessionId);
        // Broadcast question_started — wire SessionBroadcastService in M13 (T083)

        return new QuestionStartResponse(question.getId(), question.getStatus(), now, endsAt);
    }

    // T069 — end (running → ended)
    @Transactional
    public QuestionEndResponse end(UUID sessionId, UUID questionId) {
        Question question = questionRepository.findByIdAndSession_Id(questionId, sessionId)
                .orElseThrow(() -> new NotFoundException("Question not found"));

        if (question.getStatus() != QuestionStatus.running) {
            throw new BusinessException("QUESTION_NOT_RUNNING", "Question is not running");
        }

        Instant now = Instant.now();
        question.setStatus(QuestionStatus.ended);
        question.setEndedAt(now);
        questionRepository.save(question);

        questionTimerService.cancelTimer(questionId);
        redisTemplate.delete("session:" + sessionId + ":active_question");

        log.info("Ended question {} in session {}", questionId, sessionId);
        // Broadcast question_ended — wire SessionBroadcastService in M13 (T083)

        return new QuestionEndResponse(question.getId(), question.getStatus(), now);
    }

    // T069 — stats
    @Transactional(readOnly = true)
    public QuestionStatsDto getStats(UUID sessionId, UUID questionId) {
        Question question = questionRepository.findByIdAndSession_Id(questionId, sessionId)
                .orElseThrow(() -> new NotFoundException("Question not found"));

        List<StudentAnswer> answers = studentAnswerRepository.findByQuestion_Id(questionId);
        List<UUID> activeStudentIds = sessionPresenceRepository.findActiveStudentIds(sessionId);

        int totalStudents = activeStudentIds.size();
        int answeredCount = answers.size();
        int skippedCount = Math.max(0, totalStudents - answeredCount);
        int correctCount = (int) answers.stream().filter(a -> Boolean.TRUE.equals(a.getCorrect())).count();
        int wrongCount = (int) answers.stream().filter(a -> Boolean.FALSE.equals(a.getCorrect())).count();

        // Option distribution
        Map<UUID, Integer> optionCountMap = new HashMap<>();
        for (StudentAnswer answer : answers) {
            if (answer.getSelectedOptionIds() != null) {
                for (UUID oid : answer.getSelectedOptionIds()) {
                    optionCountMap.merge(oid, 1, Integer::sum);
                }
            }
        }

        List<QuestionStatsDto.OptionDistribution> optionDistribution = question.getOptions().stream()
                .map(o -> new QuestionStatsDto.OptionDistribution(
                        o.getId(), o.getLabel(), o.getText(), o.isCorrect(),
                        optionCountMap.getOrDefault(o.getId(), 0)))
                .toList();

        // Confidence breakdown
        Map<ConfidenceLevel, Long> confMap = answers.stream()
                .filter(a -> a.getConfidence() != null)
                .collect(Collectors.groupingBy(StudentAnswer::getConfidence, Collectors.counting()));

        int noneConf = (int) answers.stream().filter(a -> a.getConfidence() == null).count() + skippedCount;

        QuestionStatsDto.ConfidenceBreakdown confidenceBreakdown = new QuestionStatsDto.ConfidenceBreakdown(
                confMap.getOrDefault(ConfidenceLevel.high, 0L).intValue(),
                confMap.getOrDefault(ConfidenceLevel.medium, 0L).intValue(),
                confMap.getOrDefault(ConfidenceLevel.low, 0L).intValue(),
                noneConf);

        // Silent students (currently active in session but no answer)
        Set<UUID> answeredIds = answers.stream()
                .map(a -> a.getStudent().getId())
                .collect(Collectors.toCollection(HashSet::new));

        List<UUID> silentIds = activeStudentIds.stream()
                .filter(id -> !answeredIds.contains(id))
                .toList();

        List<User> silentUsers = userRepository.findAllById(silentIds);
        List<QuestionStatsDto.SilentStudent> silentStudents = silentUsers.stream()
                .map(u -> new QuestionStatsDto.SilentStudent(u.getId(), u.getName(), u.getAvatarColor()))
                .toList();

        return new QuestionStatsDto(questionId, totalStudents, answeredCount, skippedCount,
                correctCount, wrongCount, optionDistribution, confidenceBreakdown, silentStudents);
    }
}
