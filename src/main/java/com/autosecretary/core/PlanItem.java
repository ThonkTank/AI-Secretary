package com.autosecretary.core;

import java.time.LocalDateTime;
import java.util.List;

/** Ephemeral plan output. It is never written back into the calendar. */
public record PlanItem(
        Obligation obligation,
        LocalDateTime suggestedStart,
        LocalDateTime suggestedEnd,
        List<String> steps) {
}
