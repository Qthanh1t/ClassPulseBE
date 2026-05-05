package com.classpulse.question;

import com.classpulse.common.exception.BusinessException;
import com.classpulse.common.exception.ConflictException;
import com.classpulse.common.exception.NotFoundException;
import com.classpulse.common.security.UserPrincipal;
import com.classpulse.session.SessionRepository;
import com.classpulse.user.Role;
import com.classpulse.user.User;
import com.classpulse.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentAnswerService {

    private final QuestionRepository questionRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final StringRedisTemplate redisTemplate;

    // T072 — submit
    @Transactional
    public StudentAnswerDto submit(UUID sessionId, UUID questionId, UUID studentId, SubmitAnswerRequest request) {
        Question question = questionRepository.findByIdAndSession_Id(questionId, sessionId)
                .orElseThrow(() -> new NotFoundException("Question not found"));

        if (question.getStatus() != QuestionStatus.running) {
            throw new BusinessException("QUESTION_NOT_RUNNING", "Question is not running");
        }

        if (studentAnswerRepository.existsByQuestion_IdAndStudent_Id(questionId, studentId)) {
            throw new ConflictException("ALREADY_ANSWERED", "You have already submitted an answer");
        }

        List<UUID> selectedIds = request.selectedOptionIds() != null ? request.selectedOptionIds() : List.of();

        // Validate selectedOptionIds belong to this question
        if (!selectedIds.isEmpty()) {
            Set<UUID> validOptionIds = question.getOptions().stream()
                    .map(QuestionOption::getId)
                    .collect(Collectors.toSet());
            boolean hasInvalid = selectedIds.stream().anyMatch(id -> !validOptionIds.contains(id));
            if (hasInvalid) {
                throw new BusinessException("INVALID_OPTION", "selectedOptionIds contains options not belonging to this question");
            }
        }

        Boolean isCorrect = computeIsCorrect(question, selectedIds);

        User student = userRepository.getReferenceById(studentId);
        UUID[] selectedArray = selectedIds.isEmpty() ? null : selectedIds.toArray(new UUID[0]);

        StudentAnswer answer = StudentAnswer.builder()
                .question(question)
                .student(student)
                .selectedOptionIds(selectedArray)
                .essayText(request.essayText())
                .confidence(request.confidence())
                .correct(isCorrect)
                .build();

        studentAnswerRepository.save(answer);

        // Track answered students in Redis (TTL 5min)
        String redisKey = "session:" + sessionId + ":question:" + questionId + ":answered";
        redisTemplate.opsForSet().add(redisKey, studentId.toString());
        redisTemplate.expire(redisKey, Duration.ofMinutes(5));

        log.info("Student {} answered question {} in session {}", studentId, questionId, sessionId);
        // Broadcast answer_aggregate to teacher — wire SessionBroadcastService in M13 (T083)

        // Reload to get answeredAt from DB auditing
        studentAnswerRepository.flush();
        return StudentAnswerDto.from(answer);
    }

    // T073 — view answers
    @Transactional(readOnly = true)
    public List<StudentAnswerDto> getAnswers(UUID sessionId, UUID questionId, Authentication authentication) {
        if (!questionRepository.findByIdAndSession_Id(questionId, sessionId).isPresent()) {
            throw new NotFoundException("Question not found");
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        if (principal.role() == Role.TEACHER) {
            return studentAnswerRepository.findByQuestion_Id(questionId)
                    .stream()
                    .map(StudentAnswerDto::from)
                    .toList();
        }

        // Student: only own answer
        return studentAnswerRepository.findByQuestion_IdAndStudent_Id(questionId, principal.userId())
                .map(StudentAnswerDto::from)
                .map(List::of)
                .orElse(List.of());
    }

    private Boolean computeIsCorrect(Question question, List<UUID> selectedIds) {
        return switch (question.getType()) {
            case single, multiple -> {
                Set<UUID> correctOptionIds = question.getOptions().stream()
                        .filter(QuestionOption::isCorrect)
                        .map(QuestionOption::getId)
                        .collect(Collectors.toSet());
                Set<UUID> selectedSet = Set.copyOf(selectedIds);
                yield correctOptionIds.equals(selectedSet);
            }
            case essay -> null;
        };
    }
}
