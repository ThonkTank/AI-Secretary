package database.task;


import androidx.room.Embedded;
import androidx.room.Relation;
import androidx.room.Ignore;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import constants.Priority;
import constants.Period;

public class Task {

    @Embedded public TaskCore core;

    @Relation(parentColumn = "id", entityColumn = "taskId")
    public List<TaskSlot> slots;

    @Relation(parentColumn = "id", entityColumn = "taskId")
    public List<TaskFollowUp> followUps;

    @Relation(parentColumn = "id", entityColumn = "taskId")
    public List<TaskPrefSlot> prefSlots;

    @Relation(parentColumn = "id", entityColumn = "taskId")
    public List<TaskParent> parents;

    @Ignore
    public List<Task> children = new ArrayList<>();
    @Ignore
    private int completions;
    @Ignore
    private boolean isComplete;
    @Ignore
    private LocalDate lastCompletion;
    @Ignore
    private LocalDate lastScheduled;
     LocalDate lastDate() {return lastScheduled != null && lastScheduled.isAfter(lastCompletion) ? lastScheduled : lastCompletion;}
    private int sinceLast() {return (int) ChronoUnit.DAYS.between(lastDate(), LocalDate.now());}

    public double remainingDays() {
        if (core.deadline != null) {
            return (double) ChronoUnit.DAYS.between(LocalDate.now(), core.deadline);
        } else if (core.repetition != null) {
            return core.repetition.remainingDays(lastCompletion);
        }
        return 1;
    }

    public double requiredDays() {
        if (core.progress.target > 0) {
            return core.progress.resetPerRep ? core.repetition.requiredDays() : core.progress.remaining() / (core.progress.repsRequired(core.minDuration)*(core.cooldown));
        } else if (core.repetition != null) {
            return core.repetition.requiredDays();
        }
        return 1;
    }

    public double agingForce() {
        double agingFactor = 10;
        return 1+(sinceLast() / agingFactor);
    }

    private void checkSlots() {
        completions = 0;
        isComplete = false;
        lastCompletion = core.created.minusDays(1);
        lastScheduled = null;
        for (TaskSlot slot : slots) {
            if (lastScheduled == null || slot.day.isAfter(lastScheduled)) {
                lastScheduled = slot.day;
            }
            if (slot.completed) {
                completions++;
                isComplete = true;
                if (slot.day.isAfter(lastCompletion)) {
                    lastCompletion = slot.day;
                }
            }
        }
    }

    public int score(LocalDateTime start, LocalDateTime end) {
        int availibleTime = (int) ChronoUnit.MINUTES.between(start, end);
        checkSlots();

        // hard constraints
        if (sinceLast() < core.cooldown) {
            return 0;
        }
        if (availibleTime < core.minDuration) {
            return 0;
        }
        if (core.progress != null
            && availibleTime < core.progress.requiredTimePerRep()
        ) {
            return 0;
        }
        if (core.closeOnMiss
            && core.deadline != null
            && LocalDate.now().isAfter(core.deadline)
        ) {
            return 0;
        }

        // prio
        int totalPrio = core.priority.value;

        // with children
        int totalChildPrio = 0;
        int nrChildren = 0;
        for (Task child : children) {
            int childPrio = child.core.priority.value;
            nrChildren++;
            totalChildPrio += childPrio;
        }
        if (nrChildren > 0) {
            int avgChildPrio = totalChildPrio / nrChildren;
            totalPrio = totalPrio * avgChildPrio;
        }
        
        // fit
        LocalTime prefStart = null;
        for (TaskPrefSlot slot : prefSlots) {
            if (start.getDayOfWeek() == slot.day) {
                prefStart = slot.start;
            }
        }  
        if (prefStart != null){
            double dif = Duration.between(start.toLocalTime(), prefStart).toMinutes() / 60.0;
            double fit = 1 - Math.abs(dif/ 8);
            totalPrio = (int) (totalPrio * fit);
        }

        // urgency
        double remainingDays = remainingDays();
        double requiredDays = requiredDays();
        int urgencyMultOnMiss = 100;
        double urgency = 0;

        urgency = remainingDays <= 0 ? urgencyMultOnMiss : (requiredDays/remainingDays);

        totalPrio = (int) (totalPrio * urgency);

        //aging
        double agingForce = agingForce();
        totalPrio = (int) (totalPrio * agingForce);

        return totalPrio;
    }

    @Ignore
    public void setId(long id) {
        core.id = id;
        for (TaskFollowUp followUp : followUps) {
            followUp.taskId = id;
        }
        for (TaskPrefSlot prefSlot : prefSlots) {
            prefSlot.taskId = id;
        }
        for (TaskSlot slot : slots) {
            slot.taskId = id;
        }
    }

    public void setParentId(long id) {
        for (TaskParent parent : parents) {
            parent.parent = id;
        }
    }


    //Leerer Construktor für Room
    public Task() {}
    //convenience Constructor für mich
    public Task(String title, int reps, int perPeriod, Period periodUnit, LocalDate deadline, int cooldown, LocalTime start, int maxDuration) {
        this.core = new TaskCore();
        this.core.title = title;
        this.core.cooldown = cooldown;
        this.core.deadline = deadline;
        this.core.maxDuration = maxDuration;
        
        this.core.repetition.reps = reps;
        this.core.repetition.perPeriod = perPeriod;
        this.core.repetition.periodUnit = periodUnit;

        this.slots = new ArrayList<>();
        this.followUps = new ArrayList<>();
        this.prefSlots = new ArrayList<>();

        TaskPrefSlot prefSlot = new TaskPrefSlot();
        prefSlot.day = LocalDate.now().getDayOfWeek();
        prefSlot.start = start;
        this.prefSlots.add(prefSlot);
    }

    public static List<Task> buildTree(List<Task> tasks) {
        Map<Long, Task> mappedTasks = new HashMap<>();
        List<Task> taskTree = new ArrayList<>();
        
        for (Task task : tasks) {
            mappedTasks.put(task.core.id, task);
        }

        for (Task task : tasks) {
            int parents = 0;
            for (TaskParent parent : task.parents) {
                mappedTasks.get(parent.parent).children.add(task);
                parents++;
            }
            if (parents == 0) {
                taskTree.add(task);
            }
        }
        return taskTree;
    }

    public static List<Task> flatten(List<Task> roots) {
        Set<Task> visited = new HashSet<>();
        List<Task> result = new ArrayList<>();
        for (Task root : roots) {
            collectAll(root, result, visited);
        }
        return result;
    }

    private static void collectAll(Task task, List<Task> result, Set<Task> visited) {
        if (!visited.add(task)) return;  // schon besucht
        result.add(task);
        for (Task child : task.children) {
            collectAll(child, result, visited);
        }
    }
}