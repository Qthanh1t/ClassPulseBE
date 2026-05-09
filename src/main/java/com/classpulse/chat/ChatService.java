package com.classpulse.chat;

import com.classpulse.breakout.BreakoutRoom;
import com.classpulse.breakout.BreakoutRoomRepository;
import com.classpulse.common.exception.BusinessException;
import com.classpulse.common.exception.ForbiddenException;
import com.classpulse.common.exception.NotFoundException;
import com.classpulse.common.security.StompPrincipal;
import com.classpulse.session.Session;
import com.classpulse.session.SessionPresenceRepository;
import com.classpulse.session.SessionRepository;
import com.classpulse.session.SessionStatus;
import com.classpulse.user.Role;
import com.classpulse.user.User;
import com.classpulse.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final SessionRepository sessionRepository;
    private final SessionPresenceRepository presenceRepository;
    private final BreakoutRoomRepository breakoutRoomRepository;
    private final UserRepository userRepository;

    @Transactional
    public ChatMessageDto send(UUID sessionId, Principal principal, ChatSendRequest request) {
        StompPrincipal sp = (StompPrincipal) principal;
        UUID senderId = sp.userId();

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        if (session.getStatus() != SessionStatus.active) {
            throw new BusinessException("SESSION_NOT_ACTIVE", "Session is not active");
        }

        if (sp.role() == Role.TEACHER) {
            boolean isOwner = session.getTeacher().getId().equals(senderId);
            if (!isOwner) throw new ForbiddenException("Not the session teacher");
        } else {
            presenceRepository.findById_SessionIdAndId_StudentId(sessionId, senderId)
                    .orElseThrow(() -> new ForbiddenException("Student not in session"));
        }

        BreakoutRoom breakoutRoom = null;
        if (request.getBreakoutRoomId() != null) {
            if (!breakoutRoomRepository.existsByIdAndBreakoutSession_Session_Id(
                    request.getBreakoutRoomId(), sessionId)) {
                throw new NotFoundException("Breakout room not found in this session");
            }
            breakoutRoom = breakoutRoomRepository.getReferenceById(request.getBreakoutRoomId());
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        ChatMessage msg = ChatMessage.builder()
                .session(session)
                .sender(sender)
                .content(request.getContent())
                .breakoutRoom(breakoutRoom)
                .build();
        chatRepository.save(msg);

        return ChatMessageDto.from(msg);
    }

    @Transactional(readOnly = true)
    public Map.Entry<List<ChatMessageDto>, ChatCursorMeta> getHistory(
            UUID sessionId, UUID beforeId, int limit) {
        List<ChatMessage> raw = (beforeId == null)
                ? chatRepository.findRecentBySessionId(sessionId, limit + 1)
                : chatRepository.findBeforeBySessionId(sessionId, beforeId, limit + 1);

        boolean hasMore = raw.size() > limit;
        List<ChatMessage> page = hasMore ? new ArrayList<>(raw.subList(0, limit)) : raw;

        List<ChatMessageDto> dtos = page.stream()
                .map(ChatMessageDto::from)
                .collect(java.util.stream.Collectors.toList());

        // DB returned DESC (newest first) → reverse to chronological
        Collections.reverse(dtos);

        UUID oldestId = dtos.isEmpty() ? null : dtos.get(0).id();
        return Map.entry(dtos, new ChatCursorMeta(hasMore, oldestId));
    }
}
