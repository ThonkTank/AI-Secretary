package de.thonktank.autosecretary.presentation.today;

import android.os.Bundle;

import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Validated process-state boundary for pending Today host work. */
final class TodayRequestSavedStateAdapter {
    private static final String ITEMS = "items";
    private static final String ID = "id";
    private static final String KIND = "kind";
    private static final String MESSAGE = "message";
    private static final String TASK_ID = "task_id";
    private static final String OCCURRENCE_ID = "occurrence_id";
    private static final String TITLE = "title";
    private static final String SLOT = "slot";
    private static final String ROUTINE = "routine";
    private static final String TERMINAL = "terminal";

    Bundle encode(List<TodayRequest> requests) {
        Bundle result = new Bundle();
        ArrayList<Bundle> items = new ArrayList<>(requests.size());
        for (TodayRequest request : requests) {
            Bundle item = new Bundle();
            item.putString(ID, request.id);
            item.putString(KIND, request.kind.name());
            item.putString(MESSAGE, request.message);
            item.putString(TASK_ID, request.taskId);
            item.putString(TITLE, request.title);
            item.putBoolean(ROUTINE, request.routine);
            if (request.target != null) {
                item.putString(OCCURRENCE_ID, request.target.occurrenceId);
                item.putString(SLOT, request.target.slot.name());
                item.putBoolean(TERMINAL, request.target.terminalCondition);
            }
            items.add(item);
        }
        result.putParcelableArrayList(ITEMS, items);
        return result;
    }

    @SuppressWarnings("deprecation")
    List<TodayRequest> decode(Bundle saved) {
        if (saved == null) return Collections.emptyList();
        ArrayList<Bundle> items = saved.getParcelableArrayList(ITEMS);
        if (items == null || items.isEmpty()) return Collections.emptyList();
        ArrayList<TodayRequest> result = new ArrayList<>(items.size());
        for (Bundle item : items) {
            if (item == null) continue;
            try {
                String id = item.getString(ID);
                TodayRequest.Kind kind = TodayRequest.Kind.valueOf(item.getString(KIND));
                if (kind == TodayRequest.Kind.ERROR || kind == TodayRequest.Kind.INFO) {
                    result.add(TodayRequest.feedback(id, kind, item.getString(MESSAGE)));
                } else if (kind == TodayRequest.Kind.REQUEST_TIMER_PERMISSIONS) {
                    result.add(TodayRequest.timerPermissions(id));
                } else if (kind == TodayRequest.Kind.CONFIRM_CLOSE) {
                    result.add(TodayRequest.close(id, item.getString(TASK_ID),
                            item.getString(TITLE)));
                } else {
                    TaskActionTarget target = TaskActionTarget.of(item.getString(TASK_ID),
                            item.getString(OCCURRENCE_ID), item.getString(TITLE),
                            TaskSlot.valueOf(item.getString(SLOT)), item.getBoolean(ROUTINE),
                            item.getBoolean(TERMINAL));
                    result.add(TodayRequest.task(id, kind, target));
                }
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // Corrupt or obsolete process state must not prevent Today from opening.
            }
        }
        return result;
    }
}
