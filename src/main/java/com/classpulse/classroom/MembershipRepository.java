package com.classpulse.classroom;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<ClassroomMembership, ClassroomMembershipId> {

    List<ClassroomMembership> findByClassroom_IdAndIsActiveTrue(UUID classroomId);

    Optional<ClassroomMembership> findByClassroom_IdAndStudent_Id(UUID classroomId, UUID studentId);

    boolean existsByClassroom_IdAndStudent_IdAndIsActiveTrue(UUID classroomId, UUID studentId);
}
