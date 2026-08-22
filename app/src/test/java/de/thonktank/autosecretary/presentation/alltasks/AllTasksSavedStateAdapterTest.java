package de.thonktank.autosecretary.presentation.alltasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class AllTasksSavedStateAdapterTest {
    private final AllTasksSavedStateAdapter adapter = new AllTasksSavedStateAdapter();

    @Test public void roundTripPreservesOnlyPresentationState() {
        String cardKey = AllTasksUiState.cardKey("task", TaskSlot.EVENING);
        AllTasksFilter filter = new AllTasksFilter("Gym", AllTasksUiState.Status.ALL,
                EnumSet.of(TaskSlot.MORNING, TaskSlot.EVENING),
                EnumSet.of(Recurrence.DAILY, Recurrence.WEEKDAYS), 4);
        AllTasksPresentationState expected = new AllTasksPresentationState(filter,
                AllTasksUiState.Mode.LIST, Collections.singleton(cardKey), false);

        Bundle encoded = adapter.encode(expected);
        AllTasksPresentationState restored = adapter.decode(encoded);

        assertEquals(expected, restored);
        assertEquals(new HashSet<>(Arrays.asList("query", "status", "slots", "recurrences",
                "weekday", "mode", "expanded_cards", "filters_expanded")),
                encoded.keySet());
        assertFalse(encoded.containsKey("expanded"));
        assertFalse(encoded.containsKey("dropdown"));
        assertFalse(encoded.containsKey("drag"));
    }

    @Test public void legacyTaskExpansionKeyIsDeliberatelyIgnored() {
        Bundle legacy = new Bundle();
        legacy.putStringArrayList("expanded", new ArrayList<>(Collections.singleton("task")));

        AllTasksPresentationState restored = adapter.decode(legacy);

        assertTrue(restored.expandedCardKeys.isEmpty());
        assertTrue(restored.filtersExpanded);
        assertEquals(AllTasksUiState.Mode.LIST, restored.mode);
    }

    @Test public void sortModeNormalizesRestoredStatusToActive() {
        Bundle stored = new Bundle();
        stored.putString("status", AllTasksUiState.Status.ARCHIVED.name());
        stored.putString("mode", AllTasksUiState.Mode.SORT.name());

        AllTasksPresentationState restored = adapter.decode(stored);

        assertEquals(AllTasksUiState.Status.ACTIVE, restored.filter.status);
        assertEquals(AllTasksUiState.Mode.SORT, restored.mode);
    }
}
