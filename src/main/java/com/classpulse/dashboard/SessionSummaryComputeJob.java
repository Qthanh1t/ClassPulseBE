package com.classpulse.dashboard;

import com.classpulse.question.Question;
import com.classpulse.question.QuestionRepository;
import com.classpulse.question.QuestionStatus;
import com.classpulse.question.StudentAnswer;
import com.classpulse.question.StudentAnswerRepository;
import com.classpulse.session.Session;
import com.classpulse.session.SessionPresence;
import com.classpulse.session.SessionPresenceRepository;
import com.classpulse.session.SessionRepository;
import com.classpulse.session.SessionStudentSummary;
import com.classpulse.session.SessionStudentSummaryId;
import com.classpulse.session.SessionStudentSummaryRepository;
import com.classpulse.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionSummaryComputeJob {

    private final SessionRepository sessionRepository;
    private final SessionPresenceRepository presenceRepository;
    private final QuestionRepository questionRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final SessionStudentSummaryRepository summaryRepository;
    private final UserRepository userRepository;

    @Async
    @Transactional
    public void computeAsync(UUID sessionId) {
        doCompute(sessionId);
    }

    @Transactional
    public void compute(UUID sessionId) {
        doCompute(sessionId);
    }

    private void doCompute(UUID sessionId) {
        log.info("Computing session summaries for session {}", sessionId);

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        List<SessionPresence> presences = presenceRepository.findBySessionId(sessionId);
        if (presences.isEmpty()) {
            log.info("No students attended session {}, skipping summary compute", sessionId);
            return;
        }

        List<Question> endedQuestions = questionRepository.findBySessionId(sessionId).stream()
                .filter(q -> q.getStatus() == QuestionStatus.ended)
                .toList();
        short totalQuestions = (short) endedQuestions.size();

        List<StudentAnswer> allAnswers = studentAnswerRepository.findBySessionId(sessionId);
        Map<UUID, List<StudentAnswer>> answersByStudent = allAnswers.stream()
                .collect(Collectors.groupingBy(a -> a.getStudent().getId()));

        List<SessionStudentSummary> summaries = new ArrayList<>();
        for (SessionPresence presence : presences) {
            UUID studentId = presence.getId().getStudentId();
            List<StudentAnswer> studentAnswers = answersByStudent.getOrDefault(studentId, List.of());

            short answeredCount = (short) studentAnswers.size();
            short correctCount = (short) studentAnswers.stream()
                    .filter(a -> Boolean.TRUE.equals(a.getCorrect()))
                    .count();
            short skippedCount = (short) Math.max(0, totalQuestions - answeredCount);

            BigDecimal scorePercent = totalQuestions > 0
                    ? BigDecimal.valueOf(correctCount * 100.0 / totalQuestions).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            SessionStudentSummaryId id = new SessionStudentSummaryId(sessionId, studentId);
            SessionStudentSummary summary = summaryRepository.findById(id)
                    .orElse(SessionStudentSummary.builder()
                            .id(id)
                            .session(session)
                            .student(userRepository.getReferenceById(studentId))
                            .build());

            summary.setTotalQuestions(totalQuestions);
            summary.setAnsweredCount(answeredCount);
            summary.setCorrectCount(correctCount);
            summary.setSkippedCount(skippedCount);
            summary.setScorePercent(scorePercent);
            summaries.add(summary);
        }

        summaryRepository.saveAll(summaries);
        log.info("Computed summaries for {} students in session {}", summaries.size(), sessionId);
    }
}
