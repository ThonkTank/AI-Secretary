package de.thonktank.autosecretary.presentation.alltasks;

import android.os.Bundle;

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
    private static final String STEP_ID = "step_id";
    private static final String TITLE = "title";
    private static final String ADD_STEP = "add_step";

    Bundle encode(List<AllTasksRequest> requests) {
        Bundle result = new Bundle();
        ArrayList<Bundle> items = new ArrayList<>(requests.size());
        for (AllTasksRequest request : requests) {
            Bundle item = new Bundle();
            item.putString(ID, request.id);
            item.putString(KIND, request.kind.name());
            item.putString(MESSAGE, request.message);
            item.putString(TASK_ID, request.taskId == null ? null : request.taskId.value);
            item.putString(STEP_ID, request.stepId == null ? null : request.stepId.value);
            item.putString(TITLE, request.title);
            item.putBoolean(ADD_STEP, request.addStep);
            items.add(item);
        }
        result.putParcelableArrayList(ITEMS, items);
        return result;
    }

    @SuppressWarnings("deprecation")
    List<AllTasksRequest> decode(Bundle saved) {
        if (saved == null) return Collections.emptyList();
        ArrayList<Bundle> items = saved.getParcelableArrayList(ITEMS);
        if (items == null || items.isEmpty()) return Collections.emptyList();
        ArrayList<AllTasksRequest> result = new ArrayList<>(items.size());
        for (Bundle item : items) {
            if (item == null) continue;
            try {
                result.add(AllTasksRequest.restore(item.getString(ID),
                        AllTasksRequest.Kind.valueOf(item.getString(KIND)),
                        item.getString(MESSAGE), item.getString(TASK_ID),
                        item.getString(STEP_ID), item.getString(TITLE),
                        item.getBoolean(ADD_STEP)));
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // A malformed old/process snapshot must not prevent the screen from opening.
            }
        }
        return result;
    }
}
