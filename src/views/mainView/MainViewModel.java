package views.mainView;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.LiveData;
import android.app.Application;

//App
import database.task.*;
import database.AppDatabase;
import services.taskPlanning.*;
import config.Preferences;
import constants.Period;
import views.models.ViewTask;

//java
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.ArrayList;

public class MainViewModel extends AndroidViewModel {
    private MutableLiveData<List<ViewTask>> checkList = new MutableLiveData<>();
    private Preferences prefs;
    private TaskDAO taskDao;

    public MainViewModel(Application app) {
        super(app);
        this.prefs = new Preferences(app);
        this.taskDao = AppDatabase.getInstance(app).taskDao();
    }

    public LiveData<List<ViewTask>> getCheckList() {
        return checkList;
    }

    public void updateList() {
        LocalDate day = LocalDate.now();
        LocalDateTime start = LocalDateTime.of(day, prefs.readPrefTime(day, true));
        LocalDateTime end = LocalDateTime.of(day, prefs.readPrefTime(day, false));
        SlotGenerator slotGenerator = new SlotGenerator(taskDao, start, end);

        Executors.newSingleThreadExecutor().execute(() -> { 
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

            slotGenerator.generateSlots();
            List<Task> tasks = taskDao.readByDue(day);
            List<Task> taskTree = TreeBuilder.buildTree(tasks);
            List<ViewTask> viewTasks = ViewTask.fromTree(taskTree, 0);
            checkList.postValue(viewTasks);
         });
    }
}
