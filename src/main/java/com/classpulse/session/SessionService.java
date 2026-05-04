package com.classpulse.session;

import com.classpulse.classroom.ClassroomRepository;
import com.classpulse.common.exception.BusinessException;
import com.classpulse.common.exception.ConflictException;
import com.classpulse.common.exception.NotFoundException;
import com.classpulse.common.response.PageMeta;
import com.classpulse.common.security.WsTicketService;
import com.classpulse.schedule.ScheduleRepository;
import com.classpulse.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SessionPresenceRepository presenceRepository;
    private final ClassroomRepository classroomRepository;
    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final WsTicketService wsTicketService;

    // T059 — start
    @Transactional
    public SessionDto start(UUID classroomId, UUID teacherId, CreateSessionRequest request) {
        sessionRepository.findActiveByClassroomId(classroomId).ifPresent(s -> {
            throw new ConflictException("SESSION_ALREADY_ACTIVE", "Classroom already has an active session");
        });

        var classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new NotFoundException("Classroom not found"));

        var schedule = (request.getScheduleId() != null)
                ? scheduleRepository.findByIdAndClassroom_Id(request.getScheduleId(), classroomId)
                        .orElseThrow(() -> new NotFoundException("Schedule not found"))
                : null;

        var session = Session.builder()
                .classroom(classroom)
                .teacher(userRepository.getReferenceById(teacherId))
                .schedule(schedule)
                .status(SessionStatus.active)
                .startedAt(Instant.now())
                .build();
        sessionRepository.save(session);

        log.info("Teacher {} started session {} for classroom {}", teacherId, session.getId(), classroomId);

        String wsTicket = wsTicketService.generateTicket(teacherId);
        return SessionDto.forStart(session, wsTicket);
    }

    // T062 — list by classroom
    @Transactional(readOnly = true)
    public Map.Entry<List<SessionDto>, PageMeta> listByClassroom(UUID classroomId, int page, int limit) {
        Page<Session> sessionPage = sessionRepository.findByClassroomId(
                classroomId, PageRequest.of(page - 1, limit));

        List<SessionDto> dtos = sessionPage.getContent().stream()
                .map(s -> SessionDto.forListItem(
                        s,
                        0, // wired in M10 when questions table is available
                        (int) presenceRepository.countById_SessionId(s.getId())))
                .toList();

        return Map.entry(dtos, PageMeta.from(sessionPage));
    }

    // T062 — detail
    @Transactional(readOnly = true)
    public SessionDetailDto getDetail(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        int presentCount = presenceRepository.findActiveStudentIds(sessionId).size();
        return SessionDetailDto.from(session, 0, presentCount); // questionCount wired in M10
    }

    // T061 — end
    @Transactional
    public SessionEndResponse end(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        if (session.getStatus() == SessionStatus.ended) {
            throw new BusinessException("SESSION_ALREADY_ENDED", "Session is already ended");
        }

        Instant now = Instant.now();
        session.setStatus(SessionStatus.ended);
        session.setEndedAt(now);
        sessionRepository.save(session);

        long durationSeconds = session.getStartedAt() != null
                ? Duration.between(session.getStartedAt(), now).getSeconds()
                : 0;
        int studentCount = (int) presenceRepository.countById_SessionId(sessionId);

        log.info("Session {} ended after {}s with {} students", sessionId, durationSeconds, studentCount);
        // Async summary compute will be triggered in M14 (T092)

        return new SessionEndResponse(sessionId, now, durationSeconds, 0, studentCount);
    }

    // T060 — join
    @Transactional
    public JoinSessionResponse join(UUID sessionId, UUID studentId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        if (session.getStatus() == SessionStatus.ended) {
            throw new BusinessException("SESSION_ENDED", "Session has already ended");
        }

        presenceRepository.findById_SessionIdAndId_StudentId(sessionId, studentId)
                .ifPresentOrElse(
                        p -> { p.setLeftAt(null); presenceRepository.save(p); },
                        () -> presenceRepository.save(SessionPresence.builder()
                                .id(new SessionPresenceId(sessionId, studentId))
                                .session(session)
                                .student(userRepository.getReferenceById(studentId))
                                .build())
                );

        log.info("Student {} joined session {}", studentId, sessionId);

        String wsTicket = wsTicketService.generateTicket(studentId);
        return new JoinSessionResponse(
                sessionId,
                session.getClassroom().getName(),
                session.getTeacher().getName(),
                wsTicket);
    }

    // T060 — leave
    @Transactional
    public void leave(UUID sessionId, UUID studentId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new NotFoundException("Session not found");
        }
        presenceRepository.updateLeftAt(sessionId, studentId, Instant.now());
        log.info("Student {} left session {}", studentId, sessionId);
    }

    // T060 — presence list
    @Transactional(readOnly = true)
    public List<PresenceDto> getPresence(UUID sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new NotFoundException("Session not found");
        }
        List<SessionPresence> presences = presenceRepository.findBySessionId(sessionId);
        Set<UUID> activeIds = new HashSet<>(presenceRepository.findActiveStudentIds(sessionId));
        return presences.stream()
                .map(p -> PresenceDto.from(p, activeIds.contains(p.getId().getStudentId())))
                .toList();
    }
}
