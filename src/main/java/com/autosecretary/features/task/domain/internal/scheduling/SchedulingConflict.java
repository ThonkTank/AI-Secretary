package com.autosecretary.features.task.domain.internal.scheduling;

import java.time.LocalDateTime;

public class SchedulingConflict {
    public enum Reason {
        DAY_BOUNDARY,
        CALENDAR_OVERLAP,
        FIXED_VS_FIXED
    }

    public final String taskId;
    public final String taskTitle;
    public final Reason reason;
    public final LocalDateTime start;
    public final LocalDateTime end;
    public final String detail;

    public SchedulingConflict(String taskId,
                              String taskTitle,
                              Reason reason,
                              LocalDateTime start,
                              LocalDateTime end,
                              String detail) {
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.reason = reason;
        this.start = start;
        this.end = end;
        this.detail = detail;
    }
}
