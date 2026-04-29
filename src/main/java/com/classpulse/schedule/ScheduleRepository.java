package com.classpulse.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    @Query("SELECT s FROM Schedule s WHERE s.classroom.id = :classroomId ORDER BY s.scheduledDate ASC, s.startTime ASC")
    List<Schedule> findByClassroomId(@Param("classroomId") UUID classroomId);

    @Query("SELECT s FROM Schedule s WHERE s.classroom.id = :classroomId AND s.scheduledDate BETWEEN :from AND :to ORDER BY s.scheduledDate ASC, s.startTime ASC")
    List<Schedule> findByClassroomIdAndDateBetween(
            @Param("classroomId") UUID classroomId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    Optional<Schedule> findFirstByClassroom_IdAndScheduledDateGreaterThanEqualOrderByScheduledDateAscStartTimeAsc(
            UUID classroomId, LocalDate date);

    Optional<Schedule> findByIdAndClassroom_Id(UUID id, UUID classroomId);
}
