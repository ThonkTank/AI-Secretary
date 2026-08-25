package de.thonktank.autosecretary.presentation.alltasks;

import android.os.Bundle;

import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepId;
import de.thonktank.autosecretary.presentation.navigation.AppDestination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicit Bundle boundary for pending management host requests. */
final class AllTasksRequestSavedStateAdapter {
    private static final String ITEMS = "items";
    private static final String ID = "id";
    private static final String KIND = "kind";
    private static final String MESSAGE = "message";
    private static final String TASK_ID = "task_id";
    private static final String TITLE = "title";
    private static final String LEGACY_OPEN_EDITOR = "OPEN_EDITOR";
    private static final String LEGACY_STEP_ID = "step_id";
    private static final String LEGACY_ADD_STEP = "add_step";

    static final class Restored {
        final List<AllTasksRequest> requests;
        final List<AppDestination> legacyDestinations;

        Restored(List<AllTasksRequest> requests, List<AppDestination> legacyDestinations) {
            this.requests = requests;
            this.legacyDestinations = legacyDestinations;
        }
    }

    Bundle encode(List<AllTasksRequest> requests) {
        Bundle result = new Bundle();
        ArrayList<Bundle> items = new ArrayList<>(requests.size());
        for (AllTasksRequest request : requests) {
            Bundle item = new Bundle();
            item.putString(ID, request.id);
            item.putString(KIND, request.kind.name());
            item.putString(MESSAGE, request.message);
            item.putString(TASK_ID, request.taskId == null ? null : request.taskId.value);
            item.putString(TITLE, request.title);
            items.add(item);
        }
        result.putParcelableArrayList(ITEMS, items);
        return result;
    }

    @SuppressWarnings("deprecation")
    Restored decode(Bundle saved) {
        if (saved == null) return empty();
        ArrayList<Bundle> items = saved.getParcelableArrayList(ITEMS);
        if (items == null || items.isEmpty()) return empty();
        ArrayList<AllTasksRequest> result = new ArrayList<>(items.size());
        ArrayList<AppDestination> legacyDestinations = new ArrayList<>();
        for (Bundle item : items) {
            if (item == null) continue;
            try {
                if (LEGACY_OPEN_EDITOR.equals(item.getString(KIND))) {
                    TaskId taskId = TaskId.of(item.getString(TASK_ID));
                    String stepId = item.getString(LEGACY_STEP_ID);
                    if (item.getBoolean(LEGACY_ADD_STEP))
                        legacyDestinations.add(AppDestination.addStep(taskId));
                    else if (stepId != null)
                        legacyDestinations.add(AppDestination.editStep(
                                taskId, TaskStepId.of(stepId)));
                    else legacyDestinations.add(AppDestination.editTask(taskId));
                    continue;
                }
                result.add(AllTasksRequest.restore(item.getString(ID),
                        AllTasksRequest.Kind.valueOf(item.getString(KIND)),
                        item.getString(MESSAGE), item.getString(TASK_ID),
                        item.getString(TITLE)));
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // A malformed old/process snapshot must not prevent the screen from opening.
            }
        }
        return new Restored(result, legacyDestinations);
    }

    private static Restored empty() {
        return new Restored(Collections.emptyList(), Collections.emptyList());
    }
}
