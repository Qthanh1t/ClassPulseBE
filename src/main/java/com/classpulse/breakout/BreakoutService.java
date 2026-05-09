package com.classpulse.breakout;

import com.classpulse.common.exception.BusinessException;
import com.classpulse.common.exception.ConflictException;
import com.classpulse.common.exception.NotFoundException;
import com.classpulse.session.SessionPresenceRepository;
import com.classpulse.session.SessionRepository;
import com.classpulse.session.SessionStatus;
import com.classpulse.user.User;
import com.classpulse.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BreakoutService {

    private final BreakoutSessionRepository breakoutSessionRepository;
    private final BreakoutRoomRepository breakoutRoomRepository;
    private final BreakoutAssignmentRepository breakoutAssignmentRepository;
    private final SessionRepository sessionRepository;
    private final SessionPresenceRepository sessionPresenceRepository;
    private final UserRepository userRepository;

    // T077 — create
    @Transactional
    public BreakoutSessionDto create(UUID sessionId, CreateBreakoutRequest request) {
        var session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        if (session.getStatus() != SessionStatus.active) {
            throw new BusinessException("SESSION_NOT_ACTIVE", "Session is not active");
        }

        breakoutSessionRepository.findActiveBySessionId(sessionId).ifPresent(b -> {
            throw new ConflictException("BREAKOUT_ALREADY_ACTIVE", "Session already has an active breakout");
        });

        BreakoutSession breakoutSession = breakoutSessionRepository.save(
                BreakoutSession.builder().session(session).build());

        // Pre-load all assigned students in one query to avoid N+1
        List<UUID> allStudentIds = request.getRooms().stream()
                .filter(r -> r.getStudentIds() != null)
                .flatMap(r -> r.getStudentIds().stream())
                .distinct()
                .toList();
        Map<UUID, User> userMap = userRepository.findAllById(allStudentIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<BreakoutSessionDto.RoomDto> roomDtos = new ArrayList<>();
        List<CreateRoomRequest> roomRequests = request.getRooms();

        for (int i = 0; i < roomRequests.size(); i++) {
            CreateRoomRequest roomReq = roomRequests.get(i);
            BreakoutRoom room = breakoutRoomRepository.save(BreakoutRoom.builder()
                    .breakoutSession(breakoutSession)
                    .name(roomReq.getName())
                    .task(roomReq.getTask())
                    .roomOrder((short) (i + 1))
                    .build());

            List<BreakoutSessionDto.StudentInfo> students = new ArrayList<>();
            if (roomReq.getStudentIds() != null) {
                for (UUID studentId : roomReq.getStudentIds()) {
                    breakoutAssignmentRepository.save(BreakoutAssignment.builder()
                            .id(new BreakoutAssignmentId(room.getId(), studentId))
                            .room(room)
                            .student(userRepository.getReferenceById(studentId))
                            .build());
                    User u = userMap.get(studentId);
                    if (u != null) {
                        students.add(new BreakoutSessionDto.StudentInfo(u.getId(), u.getName(), u.getAvatarColor()));
                    }
                }
            }
            roomDtos.add(new BreakoutSessionDto.RoomDto(room.getId(), room.getName(), room.getTask(), room.getRoomOrder(), students));
        }

        log.info("Created breakout session {} with {} rooms in session {}",
                breakoutSession.getId(), roomRequests.size(), sessionId);

        return BreakoutSessionDto.builder()
                .breakoutSessionId(breakoutSession.getId())
                .startedAt(breakoutSession.getStartedAt())
                .rooms(roomDtos)
                .build();
    }

    // T078 — get active
    @Transactional(readOnly = true)
    public BreakoutSessionDto getActive(UUID sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new NotFoundException("Session not found");
        }
        return breakoutSessionRepository.findActiveBySessionId(sessionId)
                .map(bs -> {
                    List<BreakoutRoom> rooms = breakoutRoomRepository
                            .findByBreakoutSession_IdWithStudents(bs.getId());
                    return BreakoutSessionDto.from(bs, rooms);
                })
                .orElse(null);
    }

    // T078 — end
    @Transactional
    public BreakoutEndResponse end(UUID sessionId, UUID breakoutId) {
        BreakoutSession breakoutSession = breakoutSessionRepository.findByIdAndSession_Id(breakoutId, sessionId)
                .orElseThrow(() -> new NotFoundException("Breakout session not found"));

        if (breakoutSession.getEndedAt() != null) {
            throw new BusinessException("BREAKOUT_ALREADY_ENDED", "Breakout session is already ended");
        }

        Instant now = Instant.now();
        breakoutSession.setEndedAt(now);
        breakoutSessionRepository.save(breakoutSession);

        log.info("Ended breakout session {} in session {}", breakoutId, sessionId);

        return new BreakoutEndResponse(breakoutId, now);
    }

    // T078 — broadcast message to all rooms
    @Transactional(readOnly = true)
    public BroadcastResponse broadcast(UUID sessionId, UUID breakoutId, BroadcastRequest request) {
        BreakoutSession breakoutSession = breakoutSessionRepository.findByIdAndSession_Id(breakoutId, sessionId)
                .orElseThrow(() -> new NotFoundException("Breakout session not found"));

        if (breakoutSession.getEndedAt() != null) {
            throw new BusinessException("BREAKOUT_ENDED", "Cannot broadcast to an ended breakout");
        }

        int recipientCount = sessionPresenceRepository.findActiveStudentIds(sessionId).size();
        Instant sentAt = Instant.now();

        log.info("Broadcast message to {} students in breakout {} of session {}",
                recipientCount, breakoutId, sessionId);

        return new BroadcastResponse(sentAt, recipientCount);
    }

    // T078 — teacher join room
    @Transactional(readOnly = true)
    public JoinRoomResponse joinRoom(UUID sessionId, UUID breakoutId, UUID roomId) {
        BreakoutSession breakoutSession = breakoutSessionRepository.findByIdAndSession_Id(breakoutId, sessionId)
                .orElseThrow(() -> new NotFoundException("Breakout session not found"));

        if (breakoutSession.getEndedAt() != null) {
            throw new BusinessException("BREAKOUT_ENDED", "Breakout session is already ended");
        }

        BreakoutRoom room = breakoutRoomRepository.findByIdAndBreakoutSession_Id(roomId, breakoutId)
                .orElseThrow(() -> new NotFoundException("Breakout room not found"));

        Instant joinedAt = Instant.now();
        log.info("Teacher joined breakout room {} in breakout {} of session {}", roomId, breakoutId, sessionId);
        return new JoinRoomResponse(roomId, room.getName(), joinedAt);
    }

    // T078 — teacher leave room
    @Transactional(readOnly = true)
    public void leaveRoom(UUID sessionId, UUID breakoutId, UUID roomId) {
        BreakoutSession breakoutSession = breakoutSessionRepository.findByIdAndSession_Id(breakoutId, sessionId)
                .orElseThrow(() -> new NotFoundException("Breakout session not found"));

        if (breakoutSession.getEndedAt() != null) {
            throw new BusinessException("BREAKOUT_ENDED", "Breakout session is already ended");
        }

        breakoutRoomRepository.findByIdAndBreakoutSession_Id(roomId, breakoutId)
                .orElseThrow(() -> new NotFoundException("Breakout room not found"));

        log.info("Teacher left breakout room {} in breakout {} of session {}", roomId, breakoutId, sessionId);
    }
}
