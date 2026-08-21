package de.thonktank.autosecretary.presentation.alltasks;

import de.thonktank.autosecretary.domain.model.ScheduleEntryId;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepId;
import de.thonktank.autosecretary.domain.schedule.ScheduleMoveRequest;
import de.thonktank.autosecretary.domain.steps.StepMoveRequest;
import de.thonktank.autosecretary.domain.steps.StepSwapRequest;

import java.util.Optional;
import java.util.Set;

/** Adapts raw Android-view callbacks to typed management commands. */
public final class AllTasksCoordinator implements AllTasksView.Listener {
    public interface Host {
        void openEditor(TaskId taskId, Optional<TaskStepId> stepId, boolean addStep);
        void confirmDelete(TaskId taskId, String title);
    }

    private final AllTasksViewModel viewModel;
    private final Host host;

    public AllTasksCoordinator(AllTasksViewModel viewModel, Host host) {
        this.viewModel = viewModel;
        this.host = host;
    }

    @Override public void onQuery(String query) { viewModel.updateQuery(query); }
    @Override public void onStatus(AllTasksUiState.Status status) {
        viewModel.updateStatus(status);
    }
    @Override public void onSlots(Set<TaskSlot> slots) { viewModel.updateSlots(slots); }
    @Override public void onRecurrences(Set<de.thonktank.autosecretary.domain.model.Recurrence>
                                                recurrences) {
        viewModel.updateRecurrences(recurrences);
    }
    @Override public void onWeekday(int weekday) { viewModel.updateWeekday(weekday); }
    @Override public void onMode(AllTasksUiState.Mode mode) { viewModel.updateMode(mode); }
    @Override public void onToggleTask(String taskId) {
        viewModel.toggleTask(TaskId.of(taskId));
    }
    @Override public void onEditTask(String taskId) {
        host.openEditor(TaskId.of(taskId), Optional.empty(), false);
    }
    @Override public void onEditStep(String taskId, String stepId) {
        host.openEditor(TaskId.of(taskId), Optional.of(TaskStepId.of(stepId)), false);
    }
    @Override public void onAddStep(String taskId) {
        host.openEditor(TaskId.of(taskId), Optional.empty(), true);
    }
    @Override public void onDeleteTask(String taskId, String title) {
        host.confirmDelete(TaskId.of(taskId), title);
    }
    @Override public void onMoveSchedule(String entryId, TaskSlot slot, String beforeEntryId) {
        viewModel.moveSchedule(new ScheduleMoveRequest(ScheduleEntryId.of(entryId), slot,
                Optional.ofNullable(beforeEntryId).map(ScheduleEntryId::of)));
    }
    @Override public void onMoveStep(String stepId, String taskId, String beforeStepId) {
        viewModel.moveStep(new StepMoveRequest(TaskStepId.of(stepId), TaskId.of(taskId),
                Optional.ofNullable(beforeStepId).map(TaskStepId::of)));
    }
    @Override public void onSwapSteps(String stepId, String targetStepId) {
        viewModel.swapSteps(new StepSwapRequest(
                TaskStepId.of(stepId), TaskStepId.of(targetStepId)));
    }
}
