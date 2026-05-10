package com.classpulse.dashboard;

import com.classpulse.common.exception.BusinessException;
import com.classpulse.common.exception.NotFoundException;
import com.classpulse.question.Question;
import com.classpulse.question.QuestionRepository;
import com.classpulse.question.QuestionStatus;
import com.classpulse.question.StudentAnswer;
import com.classpulse.question.StudentAnswerRepository;
import com.classpulse.session.Session;
import com.classpulse.session.SessionRepository;
import com.classpulse.session.SessionStatus;
import com.classpulse.session.SessionStudentSummary;
import com.classpulse.session.SessionStudentSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final SessionStudentSummaryRepository summaryRepository;
    private final SessionSummaryComputeJob computeJob;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        if (session.getStatus() != SessionStatus.ended) {
            throw new BusinessException("SESSION_NOT_ENDED", "Dashboard is only available for ended sessions");
        }

        List<SessionStudentSummary> summaries = summaryRepository.findBySession_Id(sessionId);
        if (summaries.isEmpty()) {
            // On-demand compute if async job hasn't run yet (R05 mitigation)
            log.info("Summaries not found for session {}, computing on-demand", sessionId);
            computeJob.compute(sessionId);
            summaries = summaryRepository.findBySession_Id(sessionId);
        }

        List<Question> questions = questionRepository.findBySessionId(sessionId);
        List<Question> endedQuestions = questions.stream()
                .filter(q -> q.getStatus() == QuestionStatus.ended)
                .toList();

        List<StudentAnswer> allAnswers = studentAnswerRepository.findBySessionId(sessionId);
        Map<UUID, List<StudentAnswer>> answersByQuestion = allAnswers.stream()
                .collect(Collectors.groupingBy(a -> a.getQuestion().getId()));

        int participantCount = summaries.size();

        List<DashboardResponse.QuestionSummary> questionSummaries = endedQuestions.stream()
                .map(q -> buildQuestionSummary(q, answersByQuestion.getOrDefault(q.getId(), List.of()), participantCount))
                .toList();

        List<DashboardResponse.StudentResult> studentResults = summaries.stream()
                .map(s -> new DashboardResponse.StudentResult(
                        s.getId().getStudentId(),
                        s.getStudent().getName(),
                        s.getStudent().getAvatarColor(),
                        s.getAnsweredCount(),
                        s.getCorrectCount(),
                        s.getSkippedCount(),
                        s.getScorePercent()))
                .toList();

        BigDecimal avgScore = studentResults.isEmpty() ? BigDecimal.ZERO
                : studentResults.stream()
                        .map(DashboardResponse.StudentResult::scorePercent)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(studentResults.size()), 2, RoundingMode.HALF_UP);

        long durationSeconds = session.getStartedAt() != null && session.getEndedAt() != null
                ? Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds()
                : 0;

        return DashboardResponse.builder()
                .sessionId(sessionId)
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .durationSeconds(durationSeconds)
                .totalStudents(participantCount)
                .totalQuestions(endedQuestions.size())
                .overallStats(new DashboardResponse.OverallStats(avgScore, participantCount))
                .questions(questionSummaries)
                .students(studentResults)
                .build();
    }

    private DashboardResponse.QuestionSummary buildQuestionSummary(
            Question question, List<StudentAnswer> answers, int totalStudents) {

        int answeredCount = answers.size();
        int correctCount = (int) answers.stream()
                .filter(a -> Boolean.TRUE.equals(a.getCorrect()))
                .count();
        int skippedCount = Math.max(0, totalStudents - answeredCount);

        Map<UUID, Integer> optionCountMap = new HashMap<>();
        for (StudentAnswer answer : answers) {
            if (answer.getSelectedOptionIds() != null) {
                for (UUID oid : answer.getSelectedOptionIds()) {
                    optionCountMap.merge(oid, 1, Integer::sum);
                }
            }
        }

        List<DashboardResponse.OptionResult> options = question.getOptions().stream()
                .map(o -> new DashboardResponse.OptionResult(
                        o.getId(), o.getLabel(), o.getText(), o.isCorrect(),
                        optionCountMap.getOrDefault(o.getId(), 0)))
                .toList();

        return new DashboardResponse.QuestionSummary(
                question.getId(),
                question.getQuestionOrder(),
                question.getType(),
                question.getContent(),
                totalStudents,
                answeredCount,
                correctCount,
                skippedCount,
                options);
    }
}
