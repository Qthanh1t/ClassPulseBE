package com.classpulse.classroom;

import com.classpulse.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("classroomSecurity")
@RequiredArgsConstructor
public class ClassroomSecurityBean {

    private final ClassroomRepository classroomRepository;
    private final MembershipRepository membershipRepository;

    public boolean isOwner(UUID classroomId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        return classroomRepository.existsByIdAndTeacher_Id(classroomId, principal.userId());
    }

    public boolean isMember(UUID classroomId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        if (classroomRepository.existsByIdAndTeacher_Id(classroomId, principal.userId())) {
            return true;
        }
        return membershipRepository.existsByClassroom_IdAndStudent_IdAndIsActiveTrue(classroomId, principal.userId());
    }
}
