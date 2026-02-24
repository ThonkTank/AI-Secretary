package com.autosecretary.application.task;

import com.autosecretary.application.task.model.TaskListItem;
import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskDAO;
import com.autosecretary.database.task.TaskSlot;
import com.autosecretary.services.TaskCompletionService;
import com.autosecretary.services.TaskLifecycleManager;
import com.autosecretary.services.TaskCompletionService.CompletionPhase;

import java.util.concurrent.ExecutorService;

public class CheckOffTaskUseCase {
    private final TaskDAO taskDao;
    private final TaskCompletionService completionService;
    private final TaskLifecycleManager lifecycleManager;
    private final ExecutorService executor;

    public CheckOffTaskUseCase(TaskDAO taskDao, TaskCompletionService completionService,
                               TaskLifecycleManager lifecycleManager, ExecutorService executor) {
        this.taskDao = taskDao;
        this.completionService = completionService;
        this.lifecycleManager = lifecycleManager;
        this.executor = executor;
    }

    public void execute(TaskListItem listItem, Runnable onChanged) {
        executor.execute(() -> {
            if (listItem.slotId == null) {
                return;
            }

            Task task = taskDao.read(listItem.taskId);
            TaskSlot slot = findSlot(task, listItem.slotId);
            if (slot == null) {
                return;
            }

            CompletionPhase phase = completionService.checkOff(task, slot, lifecycleManager);
            if (phase == CompletionPhase.NONE) {
                return;
            }

            if (phase == CompletionPhase.COMPLETED) {
                taskDao.write(task);
            }
            taskDao.writeSlot(slot);
            onChanged.run();
        });
    }

    private TaskSlot findSlot(Task task, String slotId) {
        for (TaskSlot slot : task.slots) {
            if (slotId.equals(slot.id)) {
                return slot;
            }
        }
        return null;
    }
}
