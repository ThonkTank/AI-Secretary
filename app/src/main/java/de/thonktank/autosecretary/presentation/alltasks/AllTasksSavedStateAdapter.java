package de.thonktank.autosecretary.presentation.alltasks;

import android.os.Bundle;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** The only Android adapter for management-filter persistence. */
public final class AllTasksSavedStateAdapter {
    public Bundle encode(AllTasksFilter filter) {
        Bundle value = new Bundle();
        value.putString("query", filter.query);
        value.putString("status", filter.status.name());
        value.putString("mode", filter.mode.name());
        value.putInt("weekday", filter.weekday);
        ArrayList<String> slots = new ArrayList<>();
        for (TaskSlot slot : filter.slots) slots.add(slot.name());
        value.putStringArrayList("slots", slots);
        ArrayList<String> recurrences = new ArrayList<>();
        for (Recurrence recurrence : filter.recurrences) recurrences.add(recurrence.name());
        value.putStringArrayList("recurrences", recurrences);
        value.putStringArrayList("expanded_cards", new ArrayList<>(filter.expandedCardKeys));
        return value;
    }

    public AllTasksFilter decode(Bundle value) {
        if (value == null) return AllTasksFilter.defaults();
        Set<TaskSlot> slots = decodeEnums(TaskSlot.class, value.getStringArrayList("slots"));
        Set<Recurrence> recurrences = decodeEnums(
                Recurrence.class, value.getStringArrayList("recurrences"));
        ArrayList<String> expanded = value.getStringArrayList("expanded_cards");
        if (expanded == null) expanded = value.getStringArrayList("expanded");
        return new AllTasksFilter(value.getString("query", ""),
                enumValue(AllTasksUiState.Status.class, value.getString("status"),
                        AllTasksUiState.Status.ACTIVE), slots, recurrences,
                value.getInt("weekday"),
                enumValue(AllTasksUiState.Mode.class, value.getString("mode"),
                        AllTasksUiState.Mode.LIST),
                expanded == null ? Collections.emptySet() : new LinkedHashSet<>(expanded));
    }

    private static <T extends Enum<T>> Set<T> decodeEnums(Class<T> type,
                                                           ArrayList<String> stored) {
        Set<T> result = EnumSet.noneOf(type);
        if (stored != null) for (String value : stored)
            try { result.add(Enum.valueOf(type, value)); }
            catch (IllegalArgumentException ignored) { }
        return result;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null) return fallback;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }
}
