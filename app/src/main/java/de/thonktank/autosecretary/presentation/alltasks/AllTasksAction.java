package de.thonktank.autosecretary.presentation.alltasks;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepId;
import de.thonktank.autosecretary.domain.schedule.ScheduleMoveRequest;
import de.thonktank.autosecretary.domain.steps.StepMoveRequest;
import de.thonktank.autosecretary.domain.steps.StepSwapRequest;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Closed input boundary for every management-screen interaction. */
public abstract class AllTasksAction {
    private AllTasksAction() { }

    public static final class QueryChanged extends AllTasksAction {
        public final String value;
        private QueryChanged(String value) { this.value = value == null ? "" : value; }
    }
    public static final class StatusChanged extends AllTasksAction {
        public final AllTasksUiState.Status value;
        private StatusChanged(AllTasksUiState.Status value) { this.value = required(value); }
    }
    public static final class SlotsChanged extends AllTasksAction {
        public final Set<TaskSlot> value;
        private SlotsChanged(Set<TaskSlot> value) { this.value = immutableEnums(value); }
    }
    public static final class RecurrencesChanged extends AllTasksAction {
        public final Set<Recurrence> value;
        private RecurrencesChanged(Set<Recurrence> value) {
            this.value = immutableEnums(value);
        }
    }
    public static final class WeekdayChanged extends AllTasksAction {
        public final int value;
        private WeekdayChanged(int value) { this.value = value; }
    }
    public static final class ModeChanged extends AllTasksAction {
        public final AllTasksUiState.Mode value;
        private ModeChanged(AllTasksUiState.Mode value) { this.value = required(value); }
    }
    public static final class FiltersExpandedChanged extends AllTasksAction {
        public final boolean value;
        private FiltersExpandedChanged(boolean value) { this.value = value; }
    }
    public static final class ResetFilters extends AllTasksAction {
        private ResetFilters() { }
    }
    public static final class CardToggled extends AllTasksAction {
        public final String cardKey;
        private CardToggled(String cardKey) { this.cardKey = requiredText(cardKey); }
    }
    public static final class EditTask extends AllTasksAction {
        public final TaskId taskId;
        private EditTask(TaskId taskId) { this.taskId = required(taskId); }
    }
    public static final class EditStep extends AllTasksAction {
        public final TaskId taskId;
        public final TaskStepId stepId;
        private EditStep(TaskId taskId, TaskStepId stepId) {
            this.taskId = required(taskId);
            this.stepId = required(stepId);
        }
    }
    public static final class AddStep extends AllTasksAction {
        public final TaskId taskId;
        private AddStep(TaskId taskId) { this.taskId = required(taskId); }
    }
    public static final class DeleteRequested extends AllTasksAction {
        public final TaskId taskId;
        public final String title;
        private DeleteRequested(TaskId taskId, String title) {
            this.taskId = required(taskId);
            this.title = requiredText(title);
        }
    }
    public static final class RequestAcknowledged extends AllTasksAction {
        public final String requestId;
        private RequestAcknowledged(String requestId) {
            this.requestId = requiredText(requestId);
        }
    }
    public static final class DeleteConfirmed extends AllTasksAction {
        public final String requestId;
        private DeleteConfirmed(String requestId) { this.requestId = requiredText(requestId); }
    }
    public static final class ScheduleMoved extends AllTasksAction {
        public final ScheduleMoveRequest request;
        private ScheduleMoved(ScheduleMoveRequest request) { this.request = required(request); }
    }
    public static final class StepMoved extends AllTasksAction {
        public final StepMoveRequest request;
        private StepMoved(StepMoveRequest request) { this.request = required(request); }
    }
    public static final class StepsSwapped extends AllTasksAction {
        public final StepSwapRequest request;
        private StepsSwapped(StepSwapRequest request) { this.request = required(request); }
    }

    public static AllTasksAction queryChanged(String value) { return new QueryChanged(value); }
    public static AllTasksAction statusChanged(AllTasksUiState.Status value) {
        return new StatusChanged(value);
    }
    public static AllTasksAction slotsChanged(Set<TaskSlot> value) {
        return new SlotsChanged(value);
    }
    public static AllTasksAction recurrencesChanged(Set<Recurrence> value) {
        return new RecurrencesChanged(value);
    }
    public static AllTasksAction weekdayChanged(int value) { return new WeekdayChanged(value); }
    public static AllTasksAction modeChanged(AllTasksUiState.Mode value) {
        return new ModeChanged(value);
    }
    public static AllTasksAction filtersExpandedChanged(boolean value) {
        return new FiltersExpandedChanged(value);
    }
    public static AllTasksAction resetFilters() { return new ResetFilters(); }
    public static AllTasksAction cardToggled(String cardKey) { return new CardToggled(cardKey); }
    public static AllTasksAction editTask(TaskId taskId) { return new EditTask(taskId); }
    public static AllTasksAction editStep(TaskId taskId, TaskStepId stepId) {
        return new EditStep(taskId, stepId);
    }
    public static AllTasksAction addStep(TaskId taskId) { return new AddStep(taskId); }
    public static AllTasksAction deleteRequested(TaskId taskId, String title) {
        return new DeleteRequested(taskId, title);
    }
    public static AllTasksAction acknowledgeRequest(String requestId) {
        return new RequestAcknowledged(requestId);
    }
    public static AllTasksAction confirmDelete(String requestId) {
        return new DeleteConfirmed(requestId);
    }
    public static AllTasksAction scheduleMoved(ScheduleMoveRequest request) {
        return new ScheduleMoved(request);
    }
    public static AllTasksAction stepMoved(StepMoveRequest request) {
        return new StepMoved(request);
    }
    public static AllTasksAction stepsSwapped(StepSwapRequest request) {
        return new StepsSwapped(request);
    }

    private static String requiredText(String value) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("Text is required");
        return value;
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("Action value is required");
        return value;
    }

    private static <E extends Enum<E>> Set<E> immutableEnums(Set<E> value) {
        required(value);
        if (value.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(EnumSet.copyOf(value));
    }
}
