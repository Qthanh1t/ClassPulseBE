package com.classpulse.schedule;

import com.classpulse.classroom.ClassroomRepository;
import com.classpulse.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ClassroomRepository classroomRepository;

    @Transactional(readOnly = true)
    public List<ScheduleDto> list(UUID classroomId, LocalDate from, LocalDate to) {
        List<Schedule> schedules = (from != null && to != null)
                ? scheduleRepository.findByClassroomIdAndDateBetween(classroomId, from, to)
                : scheduleRepository.findByClassroomId(classroomId);
        return schedules.stream().map(ScheduleDto::from).toList();
    }

    @Transactional
    public ScheduleDto create(UUID classroomId, CreateScheduleRequest request) {
        var classroom = classroomRepository.getReferenceById(classroomId);
        Schedule schedule = Schedule.builder()
                .classroom(classroom)
                .title(request.getTitle())
                .scheduledDate(request.getScheduledDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .description(request.getDescription())
                .build();
        scheduleRepository.save(schedule);
        log.info("Created schedule {} in classroom {}", schedule.getId(), classroomId);
        return ScheduleDto.from(schedule);
    }

    @Transactional
    public ScheduleDto update(UUID classroomId, UUID scheduleId, UpdateScheduleRequest request) {
        Schedule schedule = findSchedule(scheduleId, classroomId);
        if (request.getTitle() != null) schedule.setTitle(request.getTitle());
        if (request.getScheduledDate() != null) schedule.setScheduledDate(request.getScheduledDate());
        if (request.getStartTime() != null) schedule.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) schedule.setEndTime(request.getEndTime());
        if (request.getDescription() != null) schedule.setDescription(request.getDescription());
        scheduleRepository.save(schedule);
        log.info("Updated schedule {} in classroom {}", scheduleId, classroomId);
        return ScheduleDto.from(schedule);
    }

    @Transactional
    public void delete(UUID classroomId, UUID scheduleId) {
        Schedule schedule = findSchedule(scheduleId, classroomId);
        // Session existence check will be added in M09 when SessionRepository is available
        scheduleRepository.delete(schedule);
        log.info("Deleted schedule {} from classroom {}", scheduleId, classroomId);
    }

    private Schedule findSchedule(UUID scheduleId, UUID classroomId) {
        return scheduleRepository.findByIdAndClassroom_Id(scheduleId, classroomId)
                .orElseThrow(() -> new NotFoundException("Schedule not found"));
    }
}
