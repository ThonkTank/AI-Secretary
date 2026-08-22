package de.thonktank.autosecretary.presentation.alltasks;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.util.ArrayList;
import java.util.List;

/** Owns drag state, drop dispatch and equivalent accessibility organization actions. */
final class AllTasksReorderController {
    private final Context context;
    private final RecyclerView list;
    private final AllTasksListAdapter adapter;
    private final AllTasksView.Listener listener;
    private AllTasksUiState state = AllTasksUiState.empty();
    private boolean dragActive;
    private String selectedStepId;

    AllTasksReorderController(Context context, RecyclerView list,
                              AllTasksListAdapter adapter, AllTasksView.Listener listener) {
        this.context = context;
        this.list = list;
        this.adapter = adapter;
        this.listener = listener;
        adapter.attachReorderController(this);
        new ItemTouchHelper(new DragCallback()).attachToRecyclerView(list);
    }

    void bind(AllTasksUiState state) { this.state = state; }

    boolean isDragActive() { return dragActive; }

    void closeTransientState() {
        selectedStepId = null;
        setDragActive(false);
    }

    void installAccessibility(View view, AllTasksRow row) {
        if (view instanceof ViewGroup && ((ViewGroup) view).getChildCount() > 0)
            ((ViewGroup) view).getChildAt(0)
                    .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        List<Action> actions = accessibilityActions(row);
        if (actions.isEmpty()) return;
        if (view instanceof ViewGroup && ((ViewGroup) view).getChildCount() > 0) {
            View child = ((ViewGroup) view).getChildAt(0);
            if (child.getContentDescription() != null) {
                view.setContentDescription(child.getContentDescription());
                child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
        }
        view.setFocusable(true);
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                                                                    AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                for (Action action : actions)
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                            action.id, context.getString(action.label)));
            }

            @Override public boolean performAccessibilityAction(View host, int action,
                                                                Bundle arguments) {
                for (Action candidate : actions)
                    if (candidate.id == action) return dispatchAccessibility(row, action);
                return super.performAccessibilityAction(host, action, arguments);
            }
        });
    }

    boolean dispatchDrag(int from, int to) {
        if (from < 0 || to < 0 || from >= adapter.getItemCount() || to >= adapter.getItemCount())
            return false;
        AllTasksRow source = adapter.rowAt(from);
        AllTasksRow target = adapter.rowAt(to);
        if (source.kind == AllTasksRow.Kind.STEP) {
            if (source.task.archived) return false;
            if (target.kind == AllTasksRow.Kind.STEP && !target.task.archived) {
                if (!source.step.id.equals(target.step.id))
                    listener.onSwapSteps(source.step.id, target.step.id);
                return true;
            }
            if (target.kind == AllTasksRow.Kind.STEP_TARGET) {
                listener.onMoveStep(source.step.id, target.taskId, target.beforeId);
                return true;
            }
            if (target.kind == AllTasksRow.Kind.TASK_HEADER && !target.task.archived) {
                listener.onMoveStep(source.step.id, target.taskId, null);
                return true;
            }
        } else if (source.kind == AllTasksRow.Kind.SCHEDULE) {
            if (target.kind == AllTasksRow.Kind.SCHEDULE_TARGET) {
                listener.onMoveSchedule(source.schedule.id, target.slot, target.beforeId);
                return true;
            }
            if (target.kind == AllTasksRow.Kind.SCHEDULE) {
                listener.onMoveSchedule(source.schedule.id, target.slot, target.schedule.id);
                return true;
            }
        }
        return false;
    }

    boolean dispatchAccessibility(AllTasksRow row, int action) {
        if (row.kind == AllTasksRow.Kind.STEP && row.task.archived) return false;
        if (action == R.id.action_step_up) return moveStepBy(row, -1);
        if (action == R.id.action_step_down) return moveStepBy(row, 1);
        if (action == R.id.action_step_previous_task) return moveStepToTask(row, -1);
        if (action == R.id.action_step_next_task) return moveStepToTask(row, 1);
        if (action == R.id.action_step_select_swap) {
            selectedStepId = row.step.id;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount(), "actions");
            list.announceForAccessibility(context.getString(R.string.a11y_step_selected));
            return true;
        }
        if (action == R.id.action_step_swap_selected && selectedStepId != null) {
            listener.onSwapSteps(selectedStepId, row.step.id);
            selectedStepId = null;
            return true;
        }
        if (action == R.id.action_schedule_up) return moveScheduleBy(row, -1);
        if (action == R.id.action_schedule_down) return moveScheduleBy(row, 1);
        if (action == R.id.action_schedule_previous_slot)
            return moveScheduleToSlot(row, TaskSlot.values()[row.slot.rank - 1]);
        if (action == R.id.action_schedule_next_slot)
            return moveScheduleToSlot(row, TaskSlot.values()[row.slot.rank + 1]);
        return false;
    }

    void setDragActive(boolean active) {
        if (dragActive == active) return;
        dragActive = active;
        adapter.notifyStepTargets();
    }

    private List<Action> accessibilityActions(AllTasksRow row) {
        List<Action> result = new ArrayList<>();
        if (row.kind == AllTasksRow.Kind.STEP && !row.task.archived) {
            if (stepIndex(row) > 0) result.add(new Action(R.id.action_step_up, R.string.a11y_step_up));
            if (stepIndex(row) < row.task.steps.size() - 1)
                result.add(new Action(R.id.action_step_down, R.string.a11y_step_down));
            if (otherTask(row.cardKey, row.taskId, -1) != null)
                result.add(new Action(R.id.action_step_previous_task,
                        R.string.a11y_step_previous_task));
            if (otherTask(row.cardKey, row.taskId, 1) != null)
                result.add(new Action(R.id.action_step_next_task, R.string.a11y_step_next_task));
            result.add(new Action(R.id.action_step_select_swap, R.string.a11y_step_select_swap));
            if (selectedStepId != null && !selectedStepId.equals(row.step.id))
                result.add(new Action(R.id.action_step_swap_selected,
                        R.string.a11y_step_swap_selected));
        } else if (row.kind == AllTasksRow.Kind.SCHEDULE) {
            int index = scheduleIndex(row);
            int inSlot = scheduleInSlot(row.slot).size();
            if (index > 0) result.add(new Action(R.id.action_schedule_up,
                    R.string.a11y_schedule_up));
            if (index < inSlot - 1) result.add(new Action(R.id.action_schedule_down,
                    R.string.a11y_schedule_down));
            if (row.slot.rank > 0) result.add(new Action(R.id.action_schedule_previous_slot,
                    R.string.a11y_schedule_previous_slot));
            if (row.slot.rank < TaskSlot.values().length - 1)
                result.add(new Action(R.id.action_schedule_next_slot,
                        R.string.a11y_schedule_next_slot));
        }
        return result;
    }

    private boolean moveStepBy(AllTasksRow row, int delta) {
        int index = stepIndex(row);
        int target = index + delta;
        if (target < 0 || target >= row.task.steps.size()) return false;
        String before = delta < 0 ? row.task.steps.get(target).id
                : index + 2 < row.task.steps.size() ? row.task.steps.get(index + 2).id : null;
        listener.onMoveStep(row.step.id, row.taskId, before);
        return true;
    }

    private boolean moveStepToTask(AllTasksRow row, int direction) {
        AllTasksUiState.TaskItem target = otherTask(row.cardKey, row.taskId, direction);
        if (target == null) return false;
        listener.onMoveStep(row.step.id, target.task.id.value, null);
        return true;
    }

    private AllTasksUiState.TaskItem otherTask(String cardKey, String taskId, int direction) {
        int current = -1;
        for (int index = 0; index < state.tasks.size(); index++)
            if (state.tasks.get(index).cardKey.equals(cardKey)) current = index;
        for (int index = current + direction; index >= 0 && index < state.tasks.size();
             index += direction) {
            AllTasksUiState.TaskItem candidate = state.tasks.get(index);
            if (!candidate.archived && !candidate.task.id.value.equals(taskId)) return candidate;
        }
        return null;
    }

    private int stepIndex(AllTasksRow row) {
        for (int index = 0; index < row.task.steps.size(); index++)
            if (row.task.steps.get(index).id.equals(row.step.id)) return index;
        return -1;
    }

    private boolean moveScheduleBy(AllTasksRow row, int delta) {
        List<AllTasksUiState.ScheduleItem> values = scheduleInSlot(row.slot);
        int index = scheduleIndex(row);
        int target = index + delta;
        if (target < 0 || target >= values.size()) return false;
        String before = delta < 0 ? values.get(target).id
                : index + 2 < values.size() ? values.get(index + 2).id : null;
        listener.onMoveSchedule(row.schedule.id, row.slot, before);
        return true;
    }

    private boolean moveScheduleToSlot(AllTasksRow row, TaskSlot target) {
        listener.onMoveSchedule(row.schedule.id, target, null);
        return true;
    }

    private int scheduleIndex(AllTasksRow row) {
        List<AllTasksUiState.ScheduleItem> values = scheduleInSlot(row.slot);
        for (int index = 0; index < values.size(); index++)
            if (values.get(index).id.equals(row.schedule.id)) return index;
        return -1;
    }

    private List<AllTasksUiState.ScheduleItem> scheduleInSlot(TaskSlot slot) {
        List<AllTasksUiState.ScheduleItem> result = new ArrayList<>();
        for (AllTasksUiState.ScheduleItem item : state.schedule)
            if (item.slot == slot) result.add(item);
        return result;
    }

    private final class DragCallback extends ItemTouchHelper.Callback {
        @Override public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                              @NonNull RecyclerView.ViewHolder holder) {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return 0;
            AllTasksRow row = adapter.rowAt(position);
            boolean movable = row.kind == AllTasksRow.Kind.SCHEDULE
                    || row.kind == AllTasksRow.Kind.STEP && !row.task.archived;
            return makeMovementFlags(movable ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0, 0);
        }

        @Override public boolean isLongPressDragEnabled() { return true; }

        @Override public void onSelectedChanged(RecyclerView.ViewHolder holder, int actionState) {
            super.onSelectedChanged(holder, actionState);
            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || holder == null) return;
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION
                    && adapter.rowAt(position).kind == AllTasksRow.Kind.STEP)
                setDragActive(true);
        }

        @Override public void clearView(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder holder) {
            super.clearView(recyclerView, holder);
            setDragActive(false);
        }

        @Override public boolean onMove(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder holder,
                                        @NonNull RecyclerView.ViewHolder target) {
            return dispatchDrag(holder.getBindingAdapterPosition(),
                    target.getBindingAdapterPosition());
        }

        @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) { }
    }

    private static final class Action {
        final int id;
        final int label;
        Action(int id, int label) { this.id = id; this.label = label; }
    }
}
