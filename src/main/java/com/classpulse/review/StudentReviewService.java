package com.classpulse.review;

import com.classpulse.common.exception.BusinessException;
import com.classpulse.common.exception.NotFoundException;
import com.classpulse.question.Question;
import com.classpulse.question.QuestionRepository;
import com.classpulse.question.QuestionStatus;
import com.classpulse.question.QuestionType;
import com.classpulse.question.StudentAnswer;
import com.classpulse.question.StudentAnswerRepository;
import com.classpulse.session.Session;
import com.classpulse.session.SessionRepository;
import com.classpulse.session.SessionStatus;
import com.classpulse.session.SessionStudentSummaryId;
import com.classpulse.session.SessionStudentSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentReviewService {

    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final SessionStudentSummaryRepository summaryRepository;

    @Transactional(readOnly = true)
    public ReviewResponse getReview(UUID sessionId, UUID studentId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        if (session.getStatus() != SessionStatus.ended) {
            throw new BusinessException("SESSION_NOT_ENDED", "Review is only available for ended sessions");
        }

        List<Question> endedQuestions = questionRepository.findBySessionId(sessionId).stream()
                .filter(q -> q.getStatus() == QuestionStatus.ended)
                .toList();

        List<StudentAnswer> myAnswers = studentAnswerRepository.findBySessionIdAndStudentId(sessionId, studentId);
        Map<UUID, StudentAnswer> answerByQuestionId = myAnswers.stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), Function.identity()));

        int answeredCount = myAnswers.size();
        int correctCount = (int) myAnswers.stream()
                .filter(a -> Boolean.TRUE.equals(a.getCorrect()))
                .count();
        int skippedCount = Math.max(0, endedQuestions.size() - answeredCount);

        var scorePercent = summaryRepository
                .findById(new SessionStudentSummaryId(sessionId, studentId))
                .map(s -> s.getScorePercent())
                .orElse(null);

        List<ReviewResponse.QuestionReview> questionReviews = endedQuestions.stream()
                .map(q -> buildQuestionReview(q, answerByQuestionId.get(q.getId())))
                .toList();

        return ReviewResponse.builder()
                .sessionId(sessionId)
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .totalQuestions(endedQuestions.size())
                .answeredCount(answeredCount)
                .correctCount(correctCount)
                .skippedCount(skippedCount)
                .scorePercent(scorePercent)
                .questions(questionReviews)
                .build();
    }

    private ReviewResponse.QuestionReview buildQuestionReview(Question question, StudentAnswer answer) {
        List<UUID> mySelectedOptionIds = null;
        String myEssayText = null;
        ReviewResponse.ReviewResult result;

        if (answer == null) {
            result = ReviewResponse.ReviewResult.skipped;
        } else {
            if (answer.getSelectedOptionIds() != null) {
                mySelectedOptionIds = Arrays.asList(answer.getSelectedOptionIds());
            }
            myEssayText = answer.getEssayText();

            if (question.getType() == QuestionType.essay) {
                result = ReviewResponse.ReviewResult.pending_review;
            } else {
                result = Boolean.TRUE.equals(answer.getCorrect())
                        ? ReviewResponse.ReviewResult.correct
                        : ReviewResponse.ReviewResult.wrong;
            }
        }

        Set<UUID> mySelected = mySelectedOptionIds != null ? new HashSet<>(mySelectedOptionIds) : Set.of();
        List<ReviewResponse.OptionReview> options = question.getType() != QuestionType.essay
                ? question.getOptions().stream()
                        .map(o -> new ReviewResponse.OptionReview(
                                o.getId(), o.getLabel(), o.getText(), o.isCorrect(),
                                mySelected.contains(o.getId())))
                        .toList()
                : null;

        return new ReviewResponse.QuestionReview(
                question.getId(),
                question.getQuestionOrder(),
                question.getType(),
                question.getContent(),
                mySelectedOptionIds,
                myEssayText,
                answer != null ? answer.getConfidence() : null,
                options,
                result);
    }
}
