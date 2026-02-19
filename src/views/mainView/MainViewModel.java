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

//java
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.ArrayList;

public class MainViewModel extends AndroidViewModel {
    private MutableLiveData<String> text = new MutableLiveData<>();
    private Preferences prefs;
    private TaskDAO taskDao;

    public MainViewModel(Application app) {
        super(app);
        this.prefs = new Preferences(app);
        this.taskDao = AppDatabase.getInstance(app).taskDao();
    }

    public LiveData<String> getText() {
        return text;
    }

    public void updateText() {
        LocalDate day = LocalDate.now();
        LocalDateTime start = LocalDateTime.of(day, prefs.readPrefTime(day, true));
        LocalDateTime end = LocalDateTime.of(day, prefs.readPrefTime(day, false));
        SlotGenerator slotGenerator = new SlotGenerator(taskDao, start, end);

        Executors.newSingleThreadExecutor().execute(() -> { 
            List<Task> newTasks = new ArrayList<>();
            newTasks.add(new Task("Täglich", 1, 1, Period.DAY, null, LocalDate.now().minusDays(1), 0, LocalTime.of(6, 0), LocalTime.of(8, 0)));
            newTasks.add(new Task("overdue", 1, 1, Period.DAY, LocalDate.now().minusDays(7), LocalDate.now().minusDays(8), 0, LocalTime.of(9, 0), LocalTime.of(14, 0)));
            newTasks.add(new Task("Cooldown", 1, 1, Period.WEEK, null, LocalDate.now().minusDays(1), 7, null, null));
            Task parent = new Task("Parent", 1, 1, Period.MONTH, null, LocalDate.now().minusDays(1), 0, LocalTime.of(6, 0), LocalTime.of(8, 0));
            newTasks.add(parent);
            parent.children.add(new Task("Child A", 3, 5, Period.DAY, null, LocalDate.now().minusDays(1), 0, LocalTime.of(6, 0), LocalTime.of(8, 0)));
            parent.children.add(new Task("Child B", 1, 4, Period.DAY, null, LocalDate.now().minusDays(1), 0, LocalTime.of(6, 0), LocalTime.of(8, 0)));
            Task childC = new Task("Child C", 1, 1, Period.WEEK, null, LocalDate.now().minusDays(1), 0, LocalTime.of(6, 0), LocalTime.of(8, 0));
            parent.children.add(childC);
            childC.children.add(new Task("Grandchild", 1, 1, Period.WEEK, null, LocalDate.now().minusDays(1), 0, LocalTime.of(6, 0), LocalTime.of(8, 0)));
            taskDao.writeList(newTasks);

            slotGenerator.generateSlots();
            List<Task> tasks = taskDao.readByDue(day);
            List<Task> taskTree = TreeBuilder.buildTree(tasks);
            StringBuilder sb = new StringBuilder();
            for (Task task : taskTree) {
                String line = parseTask(task, 0);
                sb.append(line);
            }
            String content = sb.toString();
            text.postValue(content);
         });
    }

    private String parseTask(Task task, int indent) {
        StringBuilder sb = new StringBuilder();
        String title = task.core.title;
        String start = "";
        String end = "";

        for (TaskSlot slot : task.slots) {
            if (slot.day.equals(LocalDate.now())) {
                start = slot.start.toString();
                end = slot.end.toString();
            }
        }

        for (int i = 0 ; i < indent ; i++) {
            sb.append("  ");
        }

        sb.append(title + start + " bis " + end + "\n");
        for (Task child : task.children) {
            sb.append(parseTask(child, indent+1));
        }
        return sb.toString();
    }
}
