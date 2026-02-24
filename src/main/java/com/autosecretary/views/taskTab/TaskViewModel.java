package com.autosecretary.views.taskTab;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.LiveData;
import android.app.Application;
import android.util.Log;

//App
import com.autosecretary.database.task.*;
import com.autosecretary.database.AppDatabase;
import com.autosecretary.services.TaskLifecycleManager;
import com.autosecretary.services.taskPlanning.*;
import com.autosecretary.config.Preferences;
import com.autosecretary.constants.Period;
import com.autosecretary.constants.Priority;
import com.autosecretary.views.models.ViewSlotList;
import com.autosecretary.views.models.ViewSlotList.ViewSlot;

//java
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.Duration;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.function.Predicate;

public class TaskViewModel extends AndroidViewModel {
    private Preferences prefs;
    private TaskDAO taskDao;
    private SlotGenerator generator;
    private ExecutorService executor;
    private TaskLifecycleManager lifecycleManager;

    private ViewSlotList masterList;                        // alle ViewSlots
    private MutableLiveData<List<ViewSlot>> displayList = new MutableLiveData();      // gefilterte Liste für die UI
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
        masterList = new ViewSlotList();
        executor.execute(() -> { 
            masterList.fromList(taskDao.readAll());
        });

        this.lifecycleManager = new TaskLifecycleManager();
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
            //clean DB
            taskDao.deleteAllCore();

            //neu einrichten
            List<Task> newTasks = new ArrayList<>();
            Task t;
            TaskPrefSlot ps;

            // === Morgenroutine (täglich, 06:00, HIGH, adaptive) ===
            t = new Task("Morgenroutine", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 30);
            t.core.priority = Priority.HIGH;
            t.core.adaptive = true;
            t.core.minDuration = 15;
            t.core.description = "Duschen, Zähneputzen, Anziehen";
            t.core.history.currentStreak = 12;
            t.core.history.completions = 30;
            t.core.history.trackedCompletions = 28;
            t.core.history.totalDuration = 700;
            newTasks.add(t);

            // === Meditation (täglich, 06:30, MEDIUM) ===
            t = new Task("Meditation", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 30), 15);
            t.core.minDuration = 10;
            t.core.history.currentStreak = 7;
            t.core.history.completions = 14;
            newTasks.add(t);

            // === Frühstück (täglich, 07:00, MEDIUM) ===
            newTasks.add(new Task("Frühstück", 1, 1, Period.DAY, null, 1, LocalTime.of(7, 0), 20));

            // === Sport (Parent, 3x/Woche Mo/Mi/Fr, 07:30, HIGH) ===
            Task sport = new Task("Sport", 3, 1, Period.WEEK, null, 1, LocalTime.of(7, 30), 60);
            sport.core.priority = Priority.HIGH;
            sport.core.minDuration = 30;
            // PrefSlots auf Mo/Mi/Fr beschränken
            sport.prefSlots.clear();
            ps = new TaskPrefSlot();
            ps.taskId = sport.core.id;
            ps.days = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
            ps.start = LocalTime.of(7, 30);
            sport.prefSlots.add(ps);
            newTasks.add(sport);

            // Aufwärmen (Child von Sport)
            Task aufwaermen = new Task("Aufwärmen", 3, 1, Period.WEEK, null, 1, LocalTime.of(7, 30), 10);
            aufwaermen.core.minDuration = 5;
            aufwaermen.prefSlots.clear();
            ps = new TaskPrefSlot();
            ps.taskId = aufwaermen.core.id;
            ps.days = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
            ps.start = LocalTime.of(7, 30);
            aufwaermen.prefSlots.add(ps);
            sport.children.add(aufwaermen);

            // Training (Child von Sport, Prerequisite: Aufwärmen)
            t = new Task("Training", 3, 1, Period.WEEK, null, 1, LocalTime.of(7, 30), 45);
            t.core.priority = Priority.HIGH;
            t.core.minDuration = 20;
            t.prerequisites.add(new TaskPrerequisite(t.core.id, aufwaermen.core.id));
            t.prefSlots.clear();
            ps = new TaskPrefSlot();
            ps.taskId = t.core.id;
            ps.days = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
            ps.start = LocalTime.of(7, 30);
            t.prefSlots.add(ps);
            sport.children.add(t);

            // === Arbeit (täglich Mo-Fr, 09:00, HIGH, langer Block) ===
            t = new Task("Arbeit", 1, 1, Period.DAY, null, 1, LocalTime.of(9, 0), 120);
            t.core.priority = Priority.HIGH;
            t.core.minDuration = 60;
            // PrefSlots auf Werktage beschränken
            t.prefSlots.clear();
            ps = new TaskPrefSlot();
            ps.taskId = t.core.id;
            ps.days = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
            ps.start = LocalTime.of(9, 0);
            t.prefSlots.add(ps);
            newTasks.add(t);

            // === Mittagspause (täglich, 12:00, MEDIUM) ===
            newTasks.add(new Task("Mittagspause", 1, 1, Period.DAY, null, 1, LocalTime.of(12, 0), 30));

            // === Lesen (täglich, 14:00, MEDIUM, Progress-Tracking) ===
            t = new Task("Lesen", 1, 1, Period.DAY, null, 1, LocalTime.of(14, 0), 30);
            t.core.minDuration = 15;
            t.core.progress.unit = "Seiten";
            t.core.progress.target = 300;
            t.core.progress.current = 85;
            t.core.progress.minPerRep = 10;
            t.core.progress.maxPerRep = 30;
            t.core.progress.totalProgress = 85;
            t.core.progress.totalTime = 510;
            newTasks.add(t);

            // === Einkaufen (1x/Woche, 10:00, MEDIUM) ===
            newTasks.add(new Task("Einkaufen", 1, 1, Period.WEEK, null, 1, LocalTime.of(10, 0), 60));

            // === Hausarbeit (1x/Woche, 10:00, LOW) ===
            t = new Task("Hausarbeit", 1, 1, Period.WEEK, null, 1, LocalTime.of(10, 0), 45);
            t.core.priority = Priority.LOW;
            t.core.minDuration = 20;
            newTasks.add(t);

            // === Steuererklärung (1x/Monat, Deadline +14d, HIGH) ===
            t = new Task("Steuererklärung", 1, 1, Period.MONTH, LocalDate.now().plusDays(14), 1, LocalTime.of(10, 0), 90);
            t.core.priority = Priority.HIGH;
            t.core.minDuration = 30;
            t.core.description = "Belege sortieren und Formulare ausfüllen";
            newTasks.add(t);

            // === Abendspaziergang (täglich, 18:00, LOW — außerhalb 06-16 Fenster) ===
            t = new Task("Abendspaziergang", 1, 1, Period.DAY, null, 1, LocalTime.of(18, 0), 30);
            t.core.priority = Priority.LOW;
            t.core.minDuration = 15;
            newTasks.add(t);

            // === Tagebuch (täglich, 21:00, LOW — weit außerhalb Fenster) ===
            t = new Task("Tagebuch", 1, 1, Period.DAY, null, 1, LocalTime.of(21, 0), 15);
            t.core.priority = Priority.LOW;
            t.core.minDuration = 5;
            newTasks.add(t);

            taskDao.writeList(newTasks);

            //slots generieren
            generator.generateSlots();
            masterList.fromList(taskDao.readAll());
            Log.d("TaskVM", "fromList: " + masterList.displaySlots);
            filterList();
        });
    }

    public void filterList() {
        Predicate<ViewSlot> predicate = vs -> true;  // Start: alles durchlassen

        if (filters.day != null) {
            predicate = predicate.and(vs -> vs.slot.day.equals(filters.day));
        }
        if (!filters.displayUnscheduled) {
            predicate = predicate.and(vs -> vs.slot.start != null);
        }

        masterList.filter(predicate);
        Log.d("TaskVM", "fromList: " + masterList.displaySlots.size());

        sortList();
    }

    public void sortList() {
        Comparator<ViewSlot> comparator  = (a, b) -> 0;
        if (sorters.byScore) {
            comparator = comparator.thenComparing((a, b) -> Integer.compare(b.slot.score, a.slot.score));
        }
        if (sorters.byTime) {
            comparator = comparator.thenComparing((a, b) -> {
                if (a.slot.start == null && b.slot.start == null) return 0;
                if (a.slot.start == null) return 1;
                if (b.slot.start == null) return -1;
                return a.slot.start.compareTo(b.slot.start);
            });
        }
        if (sorters.byTitle) {
            comparator = comparator.thenComparing((a, b) -> a.task.core.title.compareTo(b.task.core.title));
        }

        masterList.sort(sorters.byTaskParent, comparator);
        Log.d("TaskVM", "fromList: " +masterList.displaySlots.size());
        displayList.postValue(masterList.displaySlots);
    }

    private static final long QUICK_TAP_THRESHOLD_SECONDS = 3;
    private static final long STALE_THRESHOLD_SECONDS = 24 * 3600;

    public void checkOff(ViewSlot viewSlot) {
        TaskSlot slot = viewSlot.slot;
        Task task = viewSlot.task;

        executor.execute(() -> {
            if (slot.completed) return;

            if (slot.realStart == null) {
                // Phase 1: Start markieren
                slot.realStart = LocalTime.now();
                taskDao.writeSlot(slot);
                filterList();
                return;
            }

            // Phase 2: Ende markieren + abschliessen
            slot.realEnd = LocalTime.now();
            slot.completed = true;

            long durationSeconds = Duration.between(slot.realStart, slot.realEnd).getSeconds();
            if (durationSeconds < 0) durationSeconds += 24 * 3600; // Mitternacht-Korrektur
            long durationMinutes = durationSeconds / 60;

            boolean isQuickTap = durationSeconds <= QUICK_TAP_THRESHOLD_SECONDS;
            boolean isStale = durationSeconds > STALE_THRESHOLD_SECONDS;
            boolean trackDuration = !isQuickTap && !isStale;

            lifecycleManager.updateStreak(task, slot);
            task.core.history.completions++;

            if (trackDuration) {
                task.core.history.trackedCompletions++;
                task.core.history.totalDuration += (int) durationMinutes;

                if (task.core.adaptive) {
                    lifecycleManager.adaptPrefSlot(task, slot);
                }
            }
            taskDao.write(task);
            taskDao.writeSlot(slot);
            filterList();
        });
    }
}
