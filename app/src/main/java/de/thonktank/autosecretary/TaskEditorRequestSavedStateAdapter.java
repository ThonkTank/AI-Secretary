package de.thonktank.autosecretary;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicit Bundle boundary for pending editor host requests. */
final class TaskEditorRequestSavedStateAdapter {
    private static final String ITEMS = "items";
    private static final String ID = "id";
    private static final String MESSAGE = "message";

    Bundle encode(List<TaskEditorRequest> requests) {
        Bundle result = new Bundle();
        ArrayList<Bundle> items = new ArrayList<>(requests.size());
        for (TaskEditorRequest request : requests) {
            Bundle item = new Bundle();
            item.putString(ID, request.id);
            item.putString(MESSAGE, request.message);
            items.add(item);
        }
        result.putParcelableArrayList(ITEMS, items);
        return result;
    }

    @SuppressWarnings("deprecation")
    List<TaskEditorRequest> decode(Bundle saved) {
        if (saved == null) return Collections.emptyList();
        ArrayList<Bundle> items = saved.getParcelableArrayList(ITEMS);
        if (items == null || items.isEmpty()) return Collections.emptyList();
        ArrayList<TaskEditorRequest> result = new ArrayList<>(items.size());
        for (Bundle item : items) {
            if (item == null) continue;
            try {
                result.add(new TaskEditorRequest(item.getString(ID), item.getString(MESSAGE)));
            } catch (IllegalArgumentException ignored) {
                // A malformed process snapshot must not prevent the editor from opening.
            }
        }
        return result;
    }
}
