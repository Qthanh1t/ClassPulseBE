package com.classpulse.admin;

import com.classpulse.classroom.ClassroomDto;
import com.classpulse.classroom.ClassroomRepository;
import com.classpulse.classroom.MembershipRepository;
import com.classpulse.common.response.PageMeta;
import com.classpulse.session.SessionRepository;
import com.classpulse.session.SessionStatus;
import com.classpulse.user.Role;
import com.classpulse.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final MembershipRepository membershipRepository;
    private final SessionRepository sessionRepository;

    @Transactional(readOnly = true)
    public AdminStatsDto getStats() {
        long totalUsers = userRepository.count();
        long teacherCount = userRepository.countByRole(Role.TEACHER);
        long studentCount = userRepository.countByRole(Role.STUDENT);
        long activeClassrooms = classroomRepository.countByIsArchivedFalse();
        long archivedClassrooms = classroomRepository.countByIsArchivedTrue();
        long activeSessions = sessionRepository.countByStatus(SessionStatus.active);

        return new AdminStatsDto(totalUsers, teacherCount, studentCount,
                activeClassrooms, archivedClassrooms, activeSessions);
    }

    @Transactional(readOnly = true)
    public Map.Entry<List<ClassroomDto>, PageMeta> listClassrooms(String search, int page, int limit) {
        var classroomPage = (search != null && !search.isBlank())
                ? classroomRepository.findAllFiltered(search.trim(), PageRequest.of(page - 1, limit))
                : classroomRepository.findAllWithTeacher(PageRequest.of(page - 1, limit));

        List<ClassroomDto> dtos = classroomPage.getContent().stream()
                .map(c -> ClassroomDto.from(c, (int) membershipRepository.countByClassroom_IdAndIsActiveTrue(c.getId())))
                .toList();

        return Map.entry(dtos, PageMeta.from(classroomPage));
    }
}
