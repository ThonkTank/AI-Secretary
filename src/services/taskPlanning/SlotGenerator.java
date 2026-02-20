package services.taskPlanning;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDate;
import java.time.LocalDateTime;

import database.*;
import database.task.*;

public class SlotGenerator {
    private TaskDAO taskDao;
    private LocalDateTime prefStart;
    private LocalDateTime prefEnd;

    public SlotGenerator(TaskDAO dao, LocalDateTime start, LocalDateTime end) {
        this.taskDao = dao;
        this.prefStart = start;
        this.prefEnd = end;
    }

    public void generateSlots() {
        // Task baum bauen
        List<Task> tasks = taskDao.readAll();
        List<Task> taskTree = TreeBuilder.buildTree(tasks);
        
        LocalDateTime cursor = prefStart;
        LocalDateTime end = prefEnd;
        assignSlot(taskTree, cursor, end);

        taskDao.writeList(taskTree);
    } 

    private LocalDateTime assignSlot(List<Task> tasks, LocalDateTime cursor, LocalDateTime end) {
        while (cursor.isBefore(end)) {
            Task bestTask = null;
            int bestScore = 0;
            for (Task task : tasks) {
                int score = task.score(cursor, end);
                if (score > bestScore) {
                    bestScore = score;
                    bestTask = task;
                    // Hard constraints werden durch score = 0 enforced
                }
            }

            if (bestScore == 0) {
                break;
            }

            TaskSlot slot = new TaskSlot();
            slot.taskId = bestTask.core.id;
            slot.score = bestScore;
            slot.day = cursor.toLocalDate();
            slot.start = cursor.toLocalTime();

            LocalDateTime slotEnd = cursor.plusMinutes(bestTask.core.maxDuration);
            LocalDateTime childEnd = assignSlot(bestTask.children, cursor, slotEnd);
            slotEnd = childEnd.isAfter(cursor) ? childEnd : slotEnd;
            slot.end = slotEnd.toLocalTime();
            bestTask.slots.add(slot);

            cursor = slotEnd;
        }

        //Gibt nur end time zurück. Veränderungen die an task.scheduledDays vorgenommen werden werden ja automatisch in taskTree behalten
        return cursor;
    }
}