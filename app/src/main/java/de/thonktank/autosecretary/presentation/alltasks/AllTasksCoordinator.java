package de.thonktank.autosecretary.presentation.alltasks;

import de.thonktank.autosecretary.domain.model.ScheduleEntryId;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepId;
import de.thonktank.autosecretary.domain.schedule.ScheduleMoveRequest;
import de.thonktank.autosecretary.domain.steps.StepMoveRequest;
import de.thonktank.autosecretary.domain.steps.StepSwapRequest;

import java.util.Set;

/** Adapts raw Android-view callbacks to typed management commands. */
public final class AllTasksCoordinator implements AllTasksView.Listener {
    private final AllTasksViewModel viewModel;

    public AllTasksCoordinator(AllTasksViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override public void onQuery(String query) {
        viewModel.dispatch(AllTasksAction.queryChanged(query));
    }
    @Override public void onStatus(AllTasksUiState.Status status) {
        viewModel.dispatch(AllTasksAction.statusChanged(status));
    }
    @Override public void onSlots(Set<TaskSlot> slots) {
        viewModel.dispatch(AllTasksAction.slotsChanged(slots));
    }
    @Override public void onRecurrences(Set<de.thonktank.autosecretary.domain.model.Recurrence>
                                                recurrences) {
        viewModel.dispatch(AllTasksAction.recurrencesChanged(recurrences));
    }
    @Override public void onWeekday(int weekday) {
        viewModel.dispatch(AllTasksAction.weekdayChanged(weekday));
    }
    @Override public void onMode(AllTasksUiState.Mode mode) {
        viewModel.dispatch(AllTasksAction.modeChanged(mode));
    }
    @Override public void onFiltersExpanded(boolean expanded) {
        viewModel.dispatch(AllTasksAction.filtersExpandedChanged(expanded));
    }
    @Override public void onResetFilters() {
        viewModel.dispatch(AllTasksAction.resetFilters());
    }
    @Override public void onToggleTask(String cardKey) {
        viewModel.dispatch(AllTasksAction.cardToggled(cardKey));
    }
    @Override public void onEditTask(String taskId) {
        viewModel.dispatch(AllTasksAction.editTask(TaskId.of(taskId)));
    }
    @Override public void onEditStep(String taskId, String stepId) {
        viewModel.dispatch(AllTasksAction.editStep(TaskId.of(taskId), TaskStepId.of(stepId)));
    }
    @Override public void onAddStep(String taskId) {
        viewModel.dispatch(AllTasksAction.addStep(TaskId.of(taskId)));
    }
    @Override public void onDeleteTask(String taskId, String title) {
        viewModel.dispatch(AllTasksAction.deleteRequested(TaskId.of(taskId), title));
    }
    @Override public void onMoveSchedule(String entryId, TaskSlot slot, String beforeEntryId) {
        viewModel.dispatch(AllTasksAction.scheduleMoved(new ScheduleMoveRequest(
                ScheduleEntryId.of(entryId), slot,
                java.util.Optional.ofNullable(beforeEntryId).map(ScheduleEntryId::of))));
    }
    @Override public void onMoveStep(String stepId, String taskId, String beforeStepId) {
        viewModel.dispatch(AllTasksAction.stepMoved(new StepMoveRequest(TaskStepId.of(stepId),
                TaskId.of(taskId),
                java.util.Optional.ofNullable(beforeStepId).map(TaskStepId::of))));
    }
    @Override public void onSwapSteps(String stepId, String targetStepId) {
        viewModel.dispatch(AllTasksAction.stepsSwapped(new StepSwapRequest(
                TaskStepId.of(stepId), TaskStepId.of(targetStepId))));
    }
}
