package de.thonktank.autosecretary.presentation.alltasks;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable owner of persistent and transient-free presentation choices for the management tab. */
public final class AllTasksPresentationState {
    public final AllTasksFilter filter;
    public final AllTasksUiState.Mode mode;
    public final Set<String> expandedCardKeys;

    public AllTasksPresentationState(AllTasksFilter filter, AllTasksUiState.Mode mode,
                                     Set<String> expandedCardKeys) {
        AllTasksUiState.Mode safeMode = mode == null ? AllTasksUiState.Mode.LIST : mode;
        AllTasksFilter safeFilter = filter == null ? AllTasksFilter.defaults() : filter;
        this.mode = safeMode;
        this.filter = safeMode == AllTasksUiState.Mode.SORT
                ? safeFilter.withStatus(AllTasksUiState.Status.ACTIVE) : safeFilter;
        this.expandedCardKeys = Collections.unmodifiableSet(new LinkedHashSet<>(
                expandedCardKeys == null ? Collections.emptySet() : expandedCardKeys));
    }

    public static AllTasksPresentationState defaults() {
        return new AllTasksPresentationState(AllTasksFilter.defaults(),
                AllTasksUiState.Mode.LIST, Collections.emptySet());
    }

    public AllTasksPresentationState withFilter(AllTasksFilter value) {
        return new AllTasksPresentationState(value, mode, expandedCardKeys);
    }

    public AllTasksPresentationState withMode(AllTasksUiState.Mode value) {
        return new AllTasksPresentationState(filter, value, expandedCardKeys);
    }

    public AllTasksPresentationState toggleExpanded(String cardKey) {
        Set<String> values = new LinkedHashSet<>(expandedCardKeys);
        if (!values.add(cardKey)) values.remove(cardKey);
        return new AllTasksPresentationState(filter, mode, values);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AllTasksPresentationState)) return false;
        AllTasksPresentationState value = (AllTasksPresentationState) other;
        return filter.equals(value.filter) && mode == value.mode
                && expandedCardKeys.equals(value.expandedCardKeys);
    }

    @Override public int hashCode() {
        return Objects.hash(filter, mode, expandedCardKeys);
    }
}
