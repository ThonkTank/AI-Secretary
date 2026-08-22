package de.thonktank.autosecretary.presentation.alltasks;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.UiStyle;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.util.Set;

/** Slim public facade for the virtualized management surface. */
@SuppressLint("ViewConstructor")
public final class AllTasksView extends LinearLayout {
    public interface Listener {
        default void onQuery(String query) { }
        default void onStatus(AllTasksUiState.Status status) { }
        default void onSlots(Set<TaskSlot> slots) { }
        default void onRecurrences(Set<Recurrence> recurrences) { }
        default void onWeekday(int weekday) { }
        default void onMode(AllTasksUiState.Mode mode) { }
        default void onFiltersExpanded(boolean expanded) { }
        default void onResetFilters() { }
        default void onToggleTask(String cardKey) { }
        default void onEditTask(String taskId) { }
        default void onEditStep(String taskId, String stepId) { }
        default void onAddStep(String taskId) { }
        default void onDeleteTask(String taskId, String title) { }
        default void onMoveSchedule(String entryId, TaskSlot slot, String beforeEntryId) { }
        default void onMoveStep(String stepId, String taskId, String beforeStepId) { }
        default void onSwapSteps(String stepId, String targetStepId) { }
    }

    private final RecyclerView list;
    private final AllTasksListAdapter adapter;
    private final AllTasksReorderController reorder;
    private final AllTasksControlsView controls;
    private AllTasksUiState state = AllTasksUiState.empty();

    public AllTasksView(Context context, Listener listener) {
        super(context);
        UiStyle style = new UiStyle(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(style.dimen(R.dimen.page_start), style.dp(16),
                style.dimen(R.dimen.page_end), 0);

        list = new RecyclerView(context);
        list.setId(R.id.all_tasks_list);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        list.setPadding(0, style.dp(10), 0, style.dp(26));
        configureListAnimations();
        adapter = new AllTasksListAdapter(context, style, listener);
        list.setAdapter(adapter);
        reorder = new AllTasksReorderController(context, list, adapter, listener);
        controls = new AllTasksControlsView(context, style, listener, list);
        addView(controls, new LayoutParams(-1, 0, 1));
    }

    public void bind(AllTasksUiState state, DayPalette palette) {
        this.state = state;
        configureListAnimations();
        reorder.bind(state);
        controls.bind(state, palette);
        adapter.bind(state, palette);
    }

    @Override protected void onDetachedFromWindow() {
        controls.closeTransientState();
        reorder.closeTransientState();
        super.onDetachedFromWindow();
    }

    private void configureListAnimations() {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            if (list != null) list.setItemAnimator(null);
            return;
        }
        if (list != null && list.getItemAnimator() instanceof DefaultItemAnimator) return;
        if (list == null) return;
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        animator.setAddDuration(240);
        animator.setRemoveDuration(240);
        animator.setMoveDuration(240);
        animator.setChangeDuration(240);
        list.setItemAnimator(animator);
    }

    int rowCountForTest() { return adapter.getItemCount(); }
    long rowIdForTest(int position) { return adapter.getItemId(position); }
    AllTasksRow adapterRowForTest(int position) { return adapter.rowAt(position); }
    boolean dragForTest(int from, int to) { return reorder.dispatchDrag(from, to); }
    boolean accessibilityActionForTest(int position, int action) {
        return reorder.dispatchAccessibility(adapter.rowAt(position), action);
    }
    int positionForTest(AllTasksRow.Kind kind, String id) {
        for (int index = 0; index < adapter.getItemCount(); index++) {
            AllTasksRow row = adapter.rowAt(index);
            if (row.kind != kind) continue;
            if (id == null || row.key.endsWith(id) || row.key.equals(id)) return index;
        }
        return -1;
    }
    RecyclerView recyclerForTest() { return list; }
    EditText searchForTest() { return controls.searchForTest(); }
    void setDragActiveForTest(boolean active) { reorder.setDragActive(active); }
    boolean filtersExpandedForTest() { return state.filtersExpanded; }
    boolean dropdownOpenForTest() { return controls.dropdownOpenForTest(); }
    boolean dragActiveForTest() { return reorder.isDragActive(); }
    View hierarchyAnchorForTest(int position) {
        return adapter.hierarchyAnchor(list, position);
    }
}
