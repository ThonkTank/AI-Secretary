package views.taskTab;

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
import views.models.ViewSlot;

//java
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.ArrayList;

public class TaskViewModel extends AndroidViewModel {
    private MutableLiveData<List<ViewSlot>> checkList = new MutableLiveData<>();
    private Preferences prefs;
    private TaskDAO taskDao;
    private ExecutorService executor; 

    public TaskViewModel(Application app) {
        super(app);
        this.prefs = new Preferences(app);
        this.taskDao = AppDatabase.getInstance(app).taskDao();
        this.executor =    Executors.newSingleThreadExecutor();
    }

    public LiveData<List<ViewSlot>> getCheckList() {
        return checkList;
    }

    public void updateList() {
        LocalDate day = LocalDate.now();
        LocalDateTime start = LocalDateTime.of(day, prefs.readPrefTime(day, true));
        LocalDateTime end = LocalDateTime.of(day, prefs.readPrefTime(day, false));
        SlotGenerator slotGenerator = new SlotGenerator(taskDao, start, end);

        executor.execute(() -> { 
            slotGenerator.generateSlots();
            List<ViewSlot> viewSlots = taskDao.readSlotsForDay(day);
            ViewSlot.assignIndents(viewSlots);
            checkList.postValue(viewSlots);
         });
    }

    public void checkOff(TaskSlot taskSlot) {
        taskSlot.completed = true;
        executor.execute(() -> { 
            taskDao.writeSlot(taskSlot);
        });
    }
}
