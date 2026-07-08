package com.classpulse.session;

import com.classpulse.classroom.ClassroomSecurityBean;
import com.classpulse.common.security.UserPrincipal;
import com.classpulse.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("sessionSecurity")
@RequiredArgsConstructor
public class SessionSecurityBean {

    private final SessionRepository sessionRepository;
    private final SessionPresenceRepository presenceRepository;
    private final ClassroomSecurityBean classroomSecurity;

    public boolean isOwner(UUID sessionId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        return sessionRepository.findTeacherIdById(sessionId)
                .map(teacherId -> teacherId.equals(principal.userId()))
                .orElse(false);
    }

    public boolean isParticipant(UUID sessionId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        if (principal.role() == Role.TEACHER) {
            return sessionRepository.findTeacherIdById(sessionId)
                    .map(teacherId -> teacherId.equals(principal.userId()))
                    .orElse(false);
        }
        return presenceRepository.findById_SessionIdAndId_StudentId(sessionId, principal.userId()).isPresent();
    }

    /**
     * True khi user là thành viên (owner hoặc HS active member) của LỚP chứa phiên này.
     * Dùng gác {@code join} — không cho HS ngoài lớp tạo presence chỉ nhờ biết sessionId.
     */
    public boolean isClassroomMember(UUID sessionId, Authentication authentication) {
        return sessionRepository.findClassroomIdById(sessionId)
                .map(classroomId -> classroomSecurity.isMember(classroomId, authentication))
                .orElse(false);
    }

    /**
     * Chặt hơn {@link #isParticipant}: yêu cầu đang HIỆN DIỆN trong phiên
     * (teacher = owner; student = có presence và {@code leftAt IS NULL}).
     * Dùng gác live media (LiveKit token) — cựu participant đã rời không mint token được nữa.
     */
    public boolean isActiveParticipant(UUID sessionId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        if (principal.role() == Role.TEACHER) {
            return sessionRepository.findTeacherIdById(sessionId)
                    .map(teacherId -> teacherId.equals(principal.userId()))
                    .orElse(false);
        }
        return presenceRepository.existsById_SessionIdAndId_StudentIdAndLeftAtIsNull(sessionId, principal.userId());
    }

    // ── Overload dùng cho WebSocket (StompPrincipal cho thẳng userId + role, không có Authentication) ──

    public boolean isOwner(UUID sessionId, UUID userId) {
        return sessionRepository.findTeacherIdById(sessionId)
                .map(teacherId -> teacherId.equals(userId))
                .orElse(false);
    }

    public boolean isParticipant(UUID sessionId, UUID userId, boolean isTeacher) {
        if (isTeacher) {
            return isOwner(sessionId, userId);
        }
        return presenceRepository.findById_SessionIdAndId_StudentId(sessionId, userId).isPresent();
    }

    public boolean isActiveParticipant(UUID sessionId, UUID userId, boolean isTeacher) {
        if (isTeacher) {
            return isOwner(sessionId, userId);
        }
        return presenceRepository.existsById_SessionIdAndId_StudentIdAndLeftAtIsNull(sessionId, userId);
    }
}
