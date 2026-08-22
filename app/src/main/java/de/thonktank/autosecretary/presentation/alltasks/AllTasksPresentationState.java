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
    public final boolean filtersExpanded;

    public AllTasksPresentationState(AllTasksFilter filter, AllTasksUiState.Mode mode,
                                     Set<String> expandedCardKeys, boolean filtersExpanded) {
        AllTasksUiState.Mode safeMode = mode == null ? AllTasksUiState.Mode.LIST : mode;
        AllTasksFilter safeFilter = filter == null ? AllTasksFilter.defaults() : filter;
        this.mode = safeMode;
        this.filter = safeMode == AllTasksUiState.Mode.SORT
                ? safeFilter.withStatus(AllTasksUiState.Status.ACTIVE) : safeFilter;
        this.expandedCardKeys = Collections.unmodifiableSet(new LinkedHashSet<>(
                expandedCardKeys == null ? Collections.emptySet() : expandedCardKeys));
        this.filtersExpanded = filtersExpanded;
    }

    public static AllTasksPresentationState defaults() {
        return new AllTasksPresentationState(AllTasksFilter.defaults(),
                AllTasksUiState.Mode.LIST, Collections.emptySet(), true);
    }

    public AllTasksPresentationState withFilter(AllTasksFilter value) {
        return new AllTasksPresentationState(value, mode, expandedCardKeys, filtersExpanded);
    }

    public AllTasksPresentationState withMode(AllTasksUiState.Mode value) {
        return new AllTasksPresentationState(filter, value, expandedCardKeys, filtersExpanded);
    }

    public AllTasksPresentationState toggleExpanded(String cardKey) {
        Set<String> values = new LinkedHashSet<>(expandedCardKeys);
        if (!values.add(cardKey)) values.remove(cardKey);
        return new AllTasksPresentationState(filter, mode, values, filtersExpanded);
    }

    public AllTasksPresentationState withFiltersExpanded(boolean value) {
        return new AllTasksPresentationState(filter, mode, expandedCardKeys, value);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AllTasksPresentationState)) return false;
        AllTasksPresentationState value = (AllTasksPresentationState) other;
        return filtersExpanded == value.filtersExpanded && filter.equals(value.filter)
                && mode == value.mode && expandedCardKeys.equals(value.expandedCardKeys);
    }

    @Override public int hashCode() {
        return Objects.hash(filter, mode, expandedCardKeys, filtersExpanded);
    }
}
