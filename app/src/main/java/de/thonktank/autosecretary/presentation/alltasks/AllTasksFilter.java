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
    /** Stable task-placement keys (task id + slot) expanded in the task catalog. */
    public final Set<String> expandedCardKeys;
    /** Compatibility alias for older callers and restored state. */
    public final Set<String> expandedTaskIds;

    public AllTasksFilter(String query, AllTasksUiState.Status status, Set<TaskSlot> slots,
                          Set<Recurrence> recurrences, int weekday,
                          AllTasksUiState.Mode mode, Set<String> expandedTaskIds) {
        this.query = query == null ? "" : query;
        this.mode = mode == null ? AllTasksUiState.Mode.LIST : mode;
        this.status = this.mode == AllTasksUiState.Mode.SORT
                ? AllTasksUiState.Status.ACTIVE
                : status == null ? AllTasksUiState.Status.ACTIVE : status;
        this.slots = Collections.unmodifiableSet(copySlots(slots));
        this.recurrences = Collections.unmodifiableSet(copyRecurrences(recurrences));
        this.weekday = weekday < 0 || weekday > 7 ? 0 : weekday;
        this.expandedCardKeys = Collections.unmodifiableSet(new LinkedHashSet<>(
                expandedTaskIds == null ? Collections.emptySet() : expandedTaskIds));
        this.expandedTaskIds = this.expandedCardKeys;
    }

    public static AllTasksFilter defaults() {
        return new AllTasksFilter("", AllTasksUiState.Status.ACTIVE, Collections.emptySet(),
                Collections.emptySet(), 0, AllTasksUiState.Mode.LIST, Collections.emptySet());
    }

    public AllTasksFilter withQuery(String value) {
        return copy(value, status, slots, recurrences, weekday, mode, expandedCardKeys);
    }
    public AllTasksFilter withStatus(AllTasksUiState.Status value) {
        return copy(query, value, slots, recurrences, weekday, mode, expandedCardKeys);
    }
    public AllTasksFilter withSlots(Set<TaskSlot> value) {
        return copy(query, status, value, recurrences, weekday, mode, expandedCardKeys);
    }
    public AllTasksFilter withRecurrences(Set<Recurrence> value) {
        return copy(query, status, slots, value, weekday, mode, expandedCardKeys);
    }
    public AllTasksFilter withWeekday(int value) {
        return copy(query, status, slots, recurrences, value, mode, expandedCardKeys);
    }
    public AllTasksFilter withMode(AllTasksUiState.Mode value) {
        AllTasksUiState.Status nextStatus = value == AllTasksUiState.Mode.SORT
                ? AllTasksUiState.Status.ACTIVE : status;
        return copy(query, nextStatus, slots, recurrences, weekday, value, expandedCardKeys);
    }
    public AllTasksFilter toggleExpanded(String cardKey) {
        Set<String> values = new LinkedHashSet<>(expandedCardKeys);
        if (!values.add(cardKey)) values.remove(cardKey);
        return copy(query, status, slots, recurrences, weekday, mode, values);
    }

    public AllTasksFilter resetVisibleFilters() {
        return copy(query, AllTasksUiState.Status.ACTIVE, Collections.emptySet(),
                Collections.emptySet(), 0, mode, expandedCardKeys);
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
