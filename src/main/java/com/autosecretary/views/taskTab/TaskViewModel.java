package com.autosecretary.views.taskTab;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.autosecretary.config.Preferences;
import com.autosecretary.database.AppDatabase;
import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskCore;
import com.autosecretary.database.task.TaskDAO;
import com.autosecretary.database.task.TaskPrefSlot;
import com.autosecretary.services.TaskCompletionService;
import com.autosecretary.services.TaskCompletionService.CompletionPhase;
import com.autosecretary.services.TaskLifecycleManager;
import com.autosecretary.services.taskPlanning.SlotGenerator;
import com.autosecretary.services.taskPlanning.TaskScorer;
import com.autosecretary.views.models.ViewSlotList;
import com.autosecretary.views.models.ViewSlotList.ViewSlot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

public class TaskViewModel extends AndroidViewModel {
    private final Preferences prefs;
    private final TaskDAO taskDao;
    private final SlotGenerator generator;
    private final ExecutorService executor;
    private final TaskLifecycleManager lifecycleManager;
    private final TaskCompletionService completionService;

    private final ViewSlotList masterList;
    private final MutableLiveData<List<ViewSlot>> displayList = new MutableLiveData<>();
    public Task selectedTask;
    public boolean isNewTask = false;

    public Filters filters = new Filters();
    public Sorters sorters = new Sorters();

    public class Filters {
        public LocalDate day;
        public boolean displayUnscheduled;
    }

    public class Sorters {
        public boolean byTaskParent;
        public boolean byScore;
        public boolean byTime;
        public boolean byTitle;
    }

    public TaskViewModel(Application app) {
        super(app);
        this.prefs = new Preferences(app);
        this.taskDao = AppDatabase.getInstance(app).taskDao();
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setUncaughtExceptionHandler((t, e) ->
                    Log.e("TaskViewModel", "Background crash", e)
            );
            return thread;
        });
        this.masterList = new ViewSlotList();
        executor.execute(() -> masterList.fromList(taskDao.readAll()));

        this.lifecycleManager = new TaskLifecycleManager();
        this.completionService = new TaskCompletionService();

        TaskScorer scorer = new TaskScorer(lifecycleManager);
        LocalDate day = LocalDate.now();
        LocalDateTime start = LocalDateTime.of(day, prefs.readPrefTime(day, true));
        LocalDateTime end = LocalDateTime.of(day, prefs.readPrefTime(day, false));
        this.generator = new SlotGenerator(taskDao, start, end, scorer);
    }

    public LiveData<List<ViewSlot>> getList() {
        return displayList;
    }

    public void createNewTask() {
        Task task = new Task();
        task.core = new TaskCore();
        task.slots = new ArrayList<>();
        task.prefSlots = new ArrayList<>();
        task.parents = new ArrayList<>();
        task.prerequisites = new ArrayList<>();

        TaskPrefSlot defaultSlot = new TaskPrefSlot();
        defaultSlot.taskId = task.core.id;
        defaultSlot.days = EnumSet.allOf(DayOfWeek.class);
        defaultSlot.start = LocalTime.of(6, 0);
        task.prefSlots.add(defaultSlot);

        selectedTask = task;
        isNewTask = true;
    }

    public void saveEditedTask() {
        executor.execute(() -> {
            taskDao.write(selectedTask);
            isNewTask = false;
            filterList();
        });
    }

    public void updateList() {
        executor.execute(() -> {
            taskDao.deleteAllCore();
            taskDao.writeList(TaskSeedDataFactory.createDefaultTasks());
            generator.generateSlots();
            masterList.fromList(taskDao.readAll());
            filterList();
        });
    }

    public void filterList() {
        Predicate<ViewSlot> predicate = TaskViewSlotQuery.buildPredicate(filters);
        masterList.filter(predicate);
        sortList();
    }

    public void sortList() {
        Comparator<ViewSlot> comparator = TaskViewSlotQuery.buildComparator(sorters);
        masterList.sort(sorters.byTaskParent, comparator);
        displayList.postValue(masterList.displaySlots);
    }

    public void checkOff(ViewSlot viewSlot) {
        Task task = viewSlot.task;
        CompletionPhase phase = completionService.checkOff(task, viewSlot.slot, lifecycleManager);
        if (phase == CompletionPhase.NONE) {
            return;
        }

        executor.execute(() -> {
            if (phase == CompletionPhase.COMPLETED) {
                taskDao.write(task);
            }
            taskDao.writeSlot(viewSlot.slot);
            filterList();
        });
    }
}
