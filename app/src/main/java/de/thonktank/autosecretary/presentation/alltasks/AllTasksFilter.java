package de.thonktank.autosecretary.presentation.alltasks;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Android-free, immutable controls for the management catalog. */
public final class AllTasksFilter {
    public final String query;
    public final AllTasksUiState.Status status;
    public final Set<TaskSlot> slots;
    public final Set<Recurrence> recurrences;
    public final int weekday;
    public final AllTasksUiState.Mode mode;
    public final Set<String> expandedTaskIds;

    public AllTasksFilter(String query, AllTasksUiState.Status status, Set<TaskSlot> slots,
                          Set<Recurrence> recurrences, int weekday,
                          AllTasksUiState.Mode mode, Set<String> expandedTaskIds) {
        this.query = query == null ? "" : query;
        this.status = status == null ? AllTasksUiState.Status.ACTIVE : status;
        this.slots = Collections.unmodifiableSet(copySlots(slots));
        this.recurrences = Collections.unmodifiableSet(copyRecurrences(recurrences));
        this.weekday = weekday < 0 || weekday > 7 ? 0 : weekday;
        this.mode = mode == null ? AllTasksUiState.Mode.LIST : mode;
        this.expandedTaskIds = Collections.unmodifiableSet(new LinkedHashSet<>(
                expandedTaskIds == null ? Collections.emptySet() : expandedTaskIds));
    }

    public static AllTasksFilter defaults() {
        return new AllTasksFilter("", AllTasksUiState.Status.ACTIVE, Collections.emptySet(),
                Collections.emptySet(), 0, AllTasksUiState.Mode.LIST, Collections.emptySet());
    }

    public AllTasksFilter withQuery(String value) {
        return copy(value, status, slots, recurrences, weekday, mode, expandedTaskIds);
    }
    public AllTasksFilter withStatus(AllTasksUiState.Status value) {
        return copy(query, value, slots, recurrences, weekday, mode, expandedTaskIds);
    }
    public AllTasksFilter withSlots(Set<TaskSlot> value) {
        return copy(query, status, value, recurrences, weekday, mode, expandedTaskIds);
    }
    public AllTasksFilter withRecurrences(Set<Recurrence> value) {
        return copy(query, status, slots, value, weekday, mode, expandedTaskIds);
    }
    public AllTasksFilter withWeekday(int value) {
        return copy(query, status, slots, recurrences, value, mode, expandedTaskIds);
    }
    public AllTasksFilter withMode(AllTasksUiState.Mode value) {
        return copy(query, status, slots, recurrences, weekday, value, expandedTaskIds);
    }
    public AllTasksFilter toggleExpanded(String taskId) {
        Set<String> values = new LinkedHashSet<>(expandedTaskIds);
        if (!values.add(taskId)) values.remove(taskId);
        return copy(query, status, slots, recurrences, weekday, mode, values);
    }

    private AllTasksFilter copy(String query, AllTasksUiState.Status status,
                                Set<TaskSlot> slots, Set<Recurrence> recurrences, int weekday,
                                AllTasksUiState.Mode mode, Set<String> expanded) {
        return new AllTasksFilter(query, status, slots, recurrences, weekday, mode, expanded);
    }

    private static EnumSet<TaskSlot> copySlots(Set<TaskSlot> values) {
        return values == null || values.isEmpty() ? EnumSet.noneOf(TaskSlot.class)
                : EnumSet.copyOf(values);
    }

    private static EnumSet<Recurrence> copyRecurrences(Set<Recurrence> values) {
        return values == null || values.isEmpty() ? EnumSet.noneOf(Recurrence.class)
                : EnumSet.copyOf(values);
    }
}
