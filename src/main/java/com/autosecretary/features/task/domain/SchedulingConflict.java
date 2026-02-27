package com.autosecretary.features.task.domain;

import java.time.LocalDate;

public record SchedulingConflict(String taskId,
                                 String title,
                                 LocalDate day,
                                 String reasonCode,
                                 String details) {
}
