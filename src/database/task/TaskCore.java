package database.task;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import constants.Priority;
import constants.Period;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity (tableName = "task_core")
public class TaskCore {
    // Basic
    @PrimaryKey()
    public String id = UUID.randomUUID().toString();
    public String title;
    public String description;

    //scheduling
    public Priority priority = Priority.MEDIUM;
    public int cooldown = 1;
    public LocalDate deadline;
    public LocalDate created = LocalDate.now();
    public boolean closeOnMiss = true; // Wenn deadline/perioden Ende etc. überschritten wird, task offen halten oder abschließen?

    public boolean adaptive;  //prefTimes an Nutzerverhalten anpassen?
    public int minDuration = 5;
    public int maxDuration = 10;

    @Embedded(prefix = "history_")
    public History history = new History();
    public boolean completed = false;

    @Embedded(prefix = "repetition_")
    public Repetition repetition = new Repetition();

    //completion tracking
    @Embedded(prefix = "progress_")
    public Progress progress = new Progress();

    // Repeat amount (5 times, one time, ten times) perPeriod (every, within two) Period (day, weeks).
    public static class Repetition {
        public int reps;
        public int finishedReps = 0;
        public double remainingReps() {return reps - finishedReps;}

        public int perPeriod;
        public Period periodUnit;
        public int periodInDays() {return periodUnit.value * perPeriod;}
        public double daysPerRep() {return (double) periodInDays() / (double) reps;}
        public double requiredDays() {return daysPerRep() * remainingReps();}
        public double remainingDays(LocalDate lastCompletion) {return (double) ChronoUnit.DAYS.between(LocalDate.now(), lastCompletion.plusDays(periodInDays()));}
    }

    public static class Progress {
        public String unit;             // Einheit (z.B. "Seiten", "Kapitel")
        public boolean resetPerRep;          // True = Progress resets jede repetition (muss dafür repetition haben)

        public int target = 0;              // Zielwert (z.B. 6), 0 = kein Tracking
        public int current;             // Aktueller Fortschritt (z.B. 3)
        public int remaining() {return target - current;}

        public int minPerRep;
        public int maxPerRep;

        public int totalProgress;
        public int totalTime = 10;

        public double repsRequired(double minDuration) {return resetPerRep ? target : Math.min(minPerRep, minDuration/timePerProgress());}
        public double timePerProgress() {return totalProgress == 0 ? totalTime : (double) totalTime / (double) totalProgress;}
        public int requiredTimePerRep() {return (int) (minPerRep * timePerProgress());}
    }

    public static class History {
        public int completions;
        public int currentStreak;
        public int nrStreaks = 1;
        public int averageStreak() {return completions/nrStreaks;}
    }


}

