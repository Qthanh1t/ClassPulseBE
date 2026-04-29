package com.classpulse.schedule;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
public class UpdateScheduleRequest {

    private String title;
    private LocalDate scheduledDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String description;
}
