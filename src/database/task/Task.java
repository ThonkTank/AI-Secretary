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
import java.util.List;


import constants.Priority;
import constants.Period;

public class Task {

    @Embedded public TaskCore core;

    @Relation(parentColumn = "id", entityColumn = "taskId")
    public List<TaskSlot> slots;

    @Relation(parentColumn = "id", entityColumn = "taskId")
    public List<TaskBlockedDay> blockedDays;

    @Relation(parentColumn = "id", entityColumn = "taskId")
    public List<TaskFollowUp> followUps;

    @Relation(parentColumn = "id", entityColumn = "taskId")
    public List<TaskPrefSlot> prefSlots;

    @Ignore
    public List<Task> children = new ArrayList<>();

    @Ignore
    public int score(LocalDateTime start, LocalDateTime end) {
        int availibleTime = (int) ChronoUnit.MINUTES.between(start, end);

        // hard constraints
        if (core.cooldown > 0
            && core.lastCompletion != null
            && !start.toLocalDate().isAfter(core.lastCompletion.plusDays(core.cooldown))
        ) {
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
        double remainingDays = core.remainingDays();
        double requiredDays = core.requiredDays();
        int urgencyMultOnMiss = 100;
        double urgency = 0;

        urgency = remainingDays <= 0 ? urgencyMultOnMiss : (requiredDays/remainingDays);

        totalPrio = (int) (totalPrio * urgency);

        //aging
        double agingForce = core.agingForce();
        totalPrio = (int) (totalPrio * agingForce);

        return totalPrio;
    }

    @Ignore
    public void setId(long id) {
        core.id = id;
        for (TaskBlockedDay day : blockedDays) {
            day.taskId = id;
        }
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

    //Leerer Construktor für Room
    public Task() {}
    //convenience Constructor für mich
    public Task(String title, int reps, int perPeriod, Period periodUnit, LocalDate deadline, LocalDate lastCompletion, int cooldown, LocalTime start, LocalTime end) {
        this.core = new TaskCore();
        this.core.title = title;
        this.core.cooldown = cooldown;
        this.core.deadline = deadline;
        this.core.repetition.reps = reps;
        this.core.repetition.perPeriod = perPeriod;
        this.core.repetition.periodUnit = periodUnit;

        this.slots = new ArrayList<>();
        this.blockedDays = new ArrayList<>();
        this.followUps = new ArrayList<>();
        this.prefSlots = new ArrayList<>();
    }
}