package de.thonktank.autosecretary;

import de.thonktank.autosecretary.data.local.RoomTaskRepository;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.TaskRepository;
import de.thonktank.autosecretary.domain.usecase.CloseOngoingTask;
import de.thonktank.autosecretary.domain.usecase.CompleteOccurrence;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.DeferTask;
import de.thonktank.autosecretary.domain.usecase.DeleteTask;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.LoadDashboard;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.MoveTask;
import de.thonktank.autosecretary.domain.usecase.ToggleStep;
import de.thonktank.autosecretary.domain.usecase.UpdateTask;
import de.thonktank.autosecretary.domain.usecase.UuidGenerator;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;

import java.util.List;

/** Temporary compatibility facade. Business behavior lives in individual use cases. */
public final class TaskService {
    private final Clock clock;
    private final DashboardUiMapper uiMapper;
    private final LoadDashboard loadDashboard;
    private final MaterializeDueOccurrences materialize;
    private final CreateTask createTask;
    private final CompleteOccurrence completeOccurrence;
    private final ToggleStep toggleStep;
    private final DeferTask deferTask;
    private final CloseOngoingTask closeOngoingTask;
    private final UpdateTask updateTask;
    private final MoveTask moveTask;
    private final DeleteTask deleteTask;

    public TaskService(AppDatabase database) {
        this(new RoomTaskRepository(database), new SystemClock(), new UuidGenerator());
    }

    TaskService(AppDatabase database, Clock clock) {
        this(new RoomTaskRepository(database), clock, new UuidGenerator());
    }

    TaskService(TaskRepository repository, Clock clock, IdGenerator ids) {
        this.clock = clock;
        this.uiMapper = new DashboardUiMapper();
        TaskOrdering ordering = new TaskOrdering();
        this.loadDashboard = new LoadDashboard(repository);
        this.materialize = new MaterializeDueOccurrences(repository, clock, ids);
        this.createTask = new CreateTask(repository, clock, ids, ordering);
        this.completeOccurrence = new CompleteOccurrence(repository, clock);
        this.toggleStep = new ToggleStep(repository);
        this.deferTask = new DeferTask(repository, loadDashboard, ordering, clock);
        this.closeOngoingTask = new CloseOngoingTask(repository, clock);
        this.updateTask = new UpdateTask(repository, ordering);
        this.moveTask = new MoveTask(repository, ordering);
        this.deleteTask = new DeleteTask(repository);
    }

    /** Pure read. It never creates occurrences or changes ordering. */
    public DashboardState dashboard() {
        java.time.LocalDate today = clock.today();
        return uiMapper.map(loadDashboard.execute(today), today);
    }

    /** Explicit command followed by a pure read for app-level refreshes. */
    public DashboardState refreshDashboard() {
        materialize.execute();
        return dashboard();
    }

    public void materializeDueTasks() {
        materialize.execute();
    }

    public void create(String title, TaskSlot slot, Recurrence recurrence, int intervalDays,
                       int weekdayMask, List<String> steps, boolean ongoing, String condition) {
        validate(title, recurrence, weekdayMask, ongoing, condition);
        createTask.execute(title, slot, recurrence, intervalDays, weekdayMask, steps, ongoing, condition);
    }

    public void update(String taskId, String title, TaskSlot slot) {
        if (title == null || title.trim().isEmpty())
            throw new IllegalArgumentException("geht so nicht: Ein kurzer Name reicht.");
        updateTask.execute(TaskId.of(taskId), title, slot);
    }

    public void move(String taskId, TaskSlot slot) {
        moveTask.execute(TaskId.of(taskId), slot);
    }

    public void delete(String taskId) {
        deleteTask.execute(TaskId.of(taskId));
    }

    public void toggleStep(String stepId) {
        toggleStep.execute(stepId);
    }

    public void complete(String occurrenceId) {
        completeOccurrence.execute(occurrenceId);
    }

    public void defer(String occurrenceOrTaskId) {
        deferTask.execute(occurrenceOrTaskId);
    }

    public void closeOngoingTask(String taskId) {
        closeOngoingTask.execute(TaskId.of(taskId));
    }

    private static void validate(String title, Recurrence recurrence, int weekdayMask,
                                 boolean ongoing, String condition) {
        if (title == null || title.trim().isEmpty())
            throw new IllegalArgumentException("geht so nicht: Ein kurzer Name reicht.");
        if (recurrence == Recurrence.WEEKDAYS && !ScheduleCalculator.hasWeekday(weekdayMask))
            throw new IllegalArgumentException("geht so nicht: Wähle mindestens einen Wochentag.");
        if (ongoing && (condition == null || condition.trim().isEmpty()))
            throw new IllegalArgumentException("geht so nicht: Ein fortlaufendes Vorhaben braucht eine Bedingung.");
    }
}
