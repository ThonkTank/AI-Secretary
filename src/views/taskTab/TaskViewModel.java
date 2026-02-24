package views.taskTab;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.LiveData;
import android.app.Application;
import android.util.Log;

//App
import database.task.*;
import database.AppDatabase;
import services.taskPlanning.*;
import config.Preferences;
import constants.Period;
import views.models.ViewSlotList;
import views.models.ViewSlotList.ViewSlot;

//java
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Predicate;

public class TaskViewModel extends AndroidViewModel {
    private Preferences prefs;
    private TaskDAO taskDao;
    private SlotGenerator generator;
    private ExecutorService executor;

    private ViewSlotList masterList;                        // alle ViewSlots
    private MutableLiveData<List<ViewSlot>> displayList = new MutableLiveData();      // gefilterte Liste für die UI
    public Task selectedTask;

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

        LocalDate day = LocalDate.now();
        LocalDateTime start = LocalDateTime.of(day, prefs.readPrefTime(day, true));
        LocalDateTime end = LocalDateTime.of(day, prefs.readPrefTime(day, false));
        this.generator = new SlotGenerator(taskDao, start, end);
    }

    public LiveData<List<ViewSlot>> getList() {
        return displayList;
    }

    public void saveEditedTask() {
        executor.execute(() -> {
            taskDao.write(selectedTask);
            filterList();
        });
    }

    public void updateList() {
        
        executor.execute(() -> {
            //clean DB
            taskDao.deleteAllCore();

            //neu einrichten
            List<Task> newTasks = new ArrayList<>();
            newTasks.add(new Task("Täglich", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 15));
            newTasks.add(new Task("overdue", 1, 1, Period.DAY, LocalDate.now().minusDays(7), 1, LocalTime.of(9, 0), 1));
            newTasks.add(new Task("Cooldown", 1, 1, Period.WEEK, null, 7, null, 15));
            Task parent = new Task("Parent", 1, 1, Period.MONTH, null, 1, LocalTime.of(6, 0), 30);
            newTasks.add(parent);
            parent.children.add(new Task("Child A", 3, 5, Period.DAY, null, 1, LocalTime.of(6, 0), 15));
            parent.children.add(new Task("Child B", 1, 4, Period.DAY, null, 1, LocalTime.of(6, 0), 5));
            Task childC = new Task("Child C", 1, 1, Period.WEEK, null, 1, LocalTime.of(6, 0), 15);
            parent.children.add(childC);
            childC.children.add(new Task("Grandchild", 1, 1, Period.WEEK, null, 1, LocalTime.of(6, 0), 20));
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
            comparator = comparator.thenComparing((a, b) -> a.slot.start.compareTo(b.slot.start));
        }
        if (sorters.byTitle) {
            comparator = comparator.thenComparing((a, b) -> a.task.core.title.compareTo(b.task.core.title));
        }

        masterList.sort(sorters.byTaskParent, comparator);
        Log.d("TaskVM", "fromList: " +masterList.displaySlots.size());
        displayList.postValue(masterList.displaySlots);
    }

    public void checkOff(TaskSlot taskSlot) {
        taskSlot.completed = true;
        executor.execute(() -> { 
            taskDao.writeSlot(taskSlot);
        });
    }
}
