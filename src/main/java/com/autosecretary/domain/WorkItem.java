package com.autosecretary.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Closed immutable task/routine hierarchy used by every inner layer. */
public sealed interface WorkItem permits Task, Routine {
    String id();
    String title();
    int durationMinutes();
    LocalDateTime deadlineAt();
    TimePreference timePreference();
    boolean flexible();
    List<Step> steps();
    LocalDateTime createdAt();
    CompletionStats stats();
    long revision();
    boolean isOpenOn(LocalDate day);
    LocalDate occurrenceDate(LocalDate day);
}
