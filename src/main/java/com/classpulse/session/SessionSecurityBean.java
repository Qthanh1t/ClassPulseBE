package com.classpulse.session;

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
}
