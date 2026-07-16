package com.autosecretary.features.task.domain.assistant;

import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * A set of proposed category, task and category-window mutations returned by the assistant, shown to
 * the user as a diff to approve before anything is written. Android-free domain value type.
 */
public record TaskAssistantProposal(List<CategoryChange> categoryChanges, List<TaskChange> taskChanges,
                                    List<WindowChange> windowChanges) {

    /** Convenience for proposals that touch only categories/tasks (no reserved category windows). */
    public TaskAssistantProposal(List<CategoryChange> categoryChanges, List<TaskChange> taskChanges) {
        this(categoryChanges, taskChanges, List.of());
    }

    /**
     * A proposed category mutation. For {@link ChangeOp#CREATE} {@code id} is null (assigned on
     * apply); for UPDATE/DELETE it references an existing category id.
     */
    public record CategoryChange(ChangeOp op, String id, String name, String icon, String colorHex) {}

    /**
     * A proposed task mutation, mirroring every field the manual task editor exposes. For
     * {@link ChangeOp#CREATE} {@code id} is null (assigned on apply); for UPDATE/DELETE it references
     * an existing task id. A field left null on an UPDATE means "unchanged" (on CREATE it means "use
     * the default"). {@code categoryName} may be null to mean "uncategorised"; it names a category
     * (existing or one created in the same proposal) and is resolved to a real id on apply, so the
     * assistant never has to invent a category id. The nested
     * {@link RepetitionChange}/{@link ProgressChange} objects and the {@code prefSlots} list are null
     * when untouched; a non-null value replaces that group wholesale. The scheduler-managed repetition
     * counters ({@code periodCompletions}/{@code periodStart}/{@code carryoverDebt}) are intentionally
     * not settable — the apply step derives them.
     */
    public record TaskChange(ChangeOp op, String id, String title, String description, String categoryName,
                             Priority priority, Boolean leisure, Boolean adaptive,
                             Integer minDuration, Integer maxDuration, Integer cooldown,
                             TaskCore.SchedulingType schedulingType, LocalDate startDate, LocalDate deadline,
                             Boolean closeOnMiss, LocalDate fixedDate, LocalTime fixedStart, LocalTime fixedEnd,
                             Integer fixedDuration, RepetitionChange repetition, ProgressChange progress,
                             Integer budgetRequiredCents, String budgetAccountId, String budgetCategoryId,
                             List<PrefSlotChange> prefSlots) {

        /** Convenience for the basic-fields-only case (repetition/scheduling/etc. left at defaults). */
        public TaskChange(ChangeOp op, String id, String title, String description, String categoryName,
                          Priority priority, Boolean leisure) {
            this(op, id, title, description, categoryName, priority, leisure, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }

    /** Proposed repetition pattern: e.g. "3× pro Woche" = reps=3, perPeriod=1, periodUnit=WEEK. */
    public record RepetitionChange(Integer reps, Integer perPeriod, Period periodUnit, Boolean completeFirst) {}

    /** Proposed progress-tracking configuration for a task (unit + target, optional per-rep bounds). */
    public record ProgressChange(Integer target, Integer current, String unit,
                                 Boolean resetPerRep, Integer minPerRep, Integer maxPerRep) {}

    /** A proposed preferred day/time pattern: {@code days} null means "any day". */
    public record PrefSlotChange(Set<DayOfWeek> days, LocalTime start) {}

    /**
     * A proposed reserved category time window ({@code TaskCategoryWindow}): for one weekday, reserve
     * {@code [startTime, endTime)} for tasks of {@code categoryId}. For {@link ChangeOp#CREATE}
     * {@code id} is null (assigned on apply); for UPDATE/DELETE it references an existing window id.
     * On an UPDATE a null field means "unchanged".
     */
    public record WindowChange(ChangeOp op, String id, DayOfWeek dayOfWeek, String categoryId,
                               LocalTime startTime, LocalTime endTime) {}

    public boolean isEmpty() {
        return categoryChanges.isEmpty() && taskChanges.isEmpty() && windowChanges.isEmpty();
    }
}
