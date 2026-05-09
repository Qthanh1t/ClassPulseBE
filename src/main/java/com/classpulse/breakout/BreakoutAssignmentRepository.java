package com.classpulse.breakout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BreakoutAssignmentRepository extends JpaRepository<BreakoutAssignment, BreakoutAssignmentId> {

    List<BreakoutAssignment> findByRoom_Id(UUID roomId);

    List<BreakoutAssignment> findByStudent_Id(UUID studentId);
}
