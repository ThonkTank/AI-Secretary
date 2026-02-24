package com.autosecretary.application.task;

import com.autosecretary.application.task.command.CheckOffTaskCommand;
import com.autosecretary.application.task.model.TaskListItem;
import com.autosecretary.application.task.model.checkoff.CheckOffResult;
import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskDAO;
import com.autosecretary.database.task.TaskSlot;
import com.autosecretary.services.TaskCompletionService;
import com.autosecretary.services.TaskCompletionService.CompletionPhase;
import com.autosecretary.services.TaskLifecycleManager;

import java.util.concurrent.ExecutorService;

public class CheckOffTaskUseCase {
    private final TaskDAO taskDao;
    private final CheckOffTaskCommand checkOffTaskCommand;
    private final ExecutorService executor;

    public CheckOffTaskUseCase(TaskDAO taskDao, TaskCompletionService completionService,
                               TaskLifecycleManager lifecycleManager, ExecutorService executor) {
        this.taskDao = taskDao;
        this.checkOffTaskCommand = new CheckOffTaskCommand(completionService, lifecycleManager);
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

            CheckOffResult result = checkOffTaskCommand.execute(task, slot);
            if (result.phase == CompletionPhase.NONE) {
                return;
            }

            if (result.phase == CompletionPhase.COMPLETED) {
                taskDao.write(result.updatedTask);
            }
            taskDao.writeSlot(result.updatedSlot);
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
