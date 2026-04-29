package com.classpulse.classroom;

import com.classpulse.common.exception.BusinessException;
import com.classpulse.common.exception.ConflictException;
import com.classpulse.common.exception.NotFoundException;
import com.classpulse.common.util.JoinCodeGenerator;
import com.classpulse.user.Role;
import com.classpulse.user.User;
import com.classpulse.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    // T037 — create
    @Transactional
    public ClassroomDto create(UUID teacherId, CreateClassroomRequest request) {
        User teacher = findUser(teacherId);
        String joinCode = generateUniqueJoinCode();

        Classroom classroom = Classroom.builder()
                .name(request.getName())
                .description(request.getDescription())
                .subject(request.getSubject())
                .joinCode(joinCode)
                .teacher(teacher)
                .build();

        classroomRepository.save(classroom);
        log.info("Teacher {} created classroom {}", teacherId, classroom.getId());
        return ClassroomDto.from(classroom, 0);
    }

    // T037 — list for current user (teacher sees own classrooms, student sees joined classrooms)
    @Transactional(readOnly = true)
    public List<ClassroomDto> listForUser(UUID userId, Role role) {
        List<Classroom> classrooms = (role == Role.TEACHER)
                ? classroomRepository.findByTeacher_IdAndIsArchivedFalse(userId)
                : classroomRepository.findByStudentId(userId);

        return classrooms.stream()
                .map(c -> ClassroomDto.from(c, studentCount(c.getId())))
                .toList();
    }

    // T037 — get by id
    @Transactional(readOnly = true)
    public ClassroomDto getById(UUID classroomId) {
        Classroom classroom = findClassroom(classroomId);
        return ClassroomDto.from(classroom, studentCount(classroomId));
    }

    // T037 — update
    @Transactional
    public ClassroomDto update(UUID classroomId, UpdateClassroomRequest request) {
        Classroom classroom = findClassroom(classroomId);
        classroom.setName(request.getName());
        classroom.setDescription(request.getDescription());
        classroom.setSubject(request.getSubject());
        if (request.getIsArchived() != null) {
            classroom.setArchived(request.getIsArchived());
        }
        classroomRepository.save(classroom);
        log.info("Updated classroom {}", classroomId);
        return ClassroomDto.from(classroom, studentCount(classroomId));
    }

    // T037 — archive (soft delete)
    @Transactional
    public void archive(UUID classroomId) {
        Classroom classroom = findClassroom(classroomId);
        classroom.setArchived(true);
        classroomRepository.save(classroom);
        log.info("Archived classroom {}", classroomId);
    }

    // T038 — student joins by joinCode
    @Transactional
    public JoinResponse join(UUID studentId, String joinCode) {
        Classroom classroom = classroomRepository.findByJoinCode(joinCode.toUpperCase())
                .orElseThrow(() -> new NotFoundException("Classroom not found"));

        if (classroom.isArchived()) {
            throw new BusinessException("CLASSROOM_ARCHIVED", "This classroom is no longer active");
        }
        if (membershipRepository.existsByClassroom_IdAndStudent_IdAndIsActiveTrue(classroom.getId(), studentId)) {
            throw new ConflictException("Already a member of this classroom");
        }

        ClassroomMembership membership = membershipRepository
                .findByClassroom_IdAndStudent_Id(classroom.getId(), studentId)
                .map(m -> { m.setActive(true); return m; })
                .orElseGet(() -> {
                    User student = findUser(studentId);
                    return ClassroomMembership.builder()
                            .id(new ClassroomMembershipId(classroom.getId(), studentId))
                            .classroom(classroom)
                            .student(student)
                            .isActive(true)
                            .build();
                });

        ClassroomMembership saved = membershipRepository.save(membership);
        log.info("Student {} joined classroom {}", studentId, classroom.getId());
        return new JoinResponse(classroom.getId(), classroom.getName(), saved.getJoinedAt());
    }

    // T038 — list active members
    @Transactional(readOnly = true)
    public List<MemberDto> listMembers(UUID classroomId) {
        findClassroom(classroomId);
        return membershipRepository.findByClassroom_IdAndIsActiveTrue(classroomId)
                .stream()
                .map(MemberDto::from)
                .toList();
    }

    // T038 — kick student
    @Transactional
    public void kickMember(UUID classroomId, UUID studentId) {
        ClassroomMembership membership = membershipRepository
                .findByClassroom_IdAndStudent_Id(classroomId, studentId)
                .filter(ClassroomMembership::isActive)
                .orElseThrow(() -> new NotFoundException("Member not found in classroom"));

        membership.setActive(false);
        membershipRepository.save(membership);
        log.info("Kicked student {} from classroom {}", studentId, classroomId);
    }

    // T038 — regenerate joinCode
    @Transactional
    public String regenerateCode(UUID classroomId) {
        Classroom classroom = findClassroom(classroomId);
        String newCode = generateUniqueJoinCode();
        classroom.setJoinCode(newCode);
        classroomRepository.save(classroom);
        log.info("Regenerated joinCode for classroom {}", classroomId);
        return newCode;
    }

    private Classroom findClassroom(UUID classroomId) {
        return classroomRepository.findById(classroomId)
                .orElseThrow(() -> new NotFoundException("Classroom not found"));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private int studentCount(UUID classroomId) {
        return (int) membershipRepository.countByClassroom_IdAndIsActiveTrue(classroomId);
    }

    private String generateUniqueJoinCode() {
        String code;
        do {
            code = JoinCodeGenerator.generate();
        } while (classroomRepository.existsByJoinCode(code));
        return code;
    }
}
