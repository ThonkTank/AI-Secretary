package com.autosecretary.features.task.domain;

import java.time.LocalDate;

public class SchedulingConflict {
    public final String taskId;
    public final String title;
    public final LocalDate day;
    public final String reasonCode;
    public final String details;

    public SchedulingConflict(String taskId,
                              String title,
                              LocalDate day,
                              String reasonCode,
                              String details) {
        this.taskId = taskId;
        this.title = title;
        this.day = day;
        this.reasonCode = reasonCode;
        this.details = details;
    }
}
