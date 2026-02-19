package services.taskPlanning;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import database.task.*;

public class TreeBuilder {
    public static List<Task> buildTree(List<Task> tasks) {
        Map<Long, Task> mappedTasks = new HashMap<>();
        List<Task> taskTree = new ArrayList<>();
        
        for (Task task : tasks) {
            mappedTasks.put(task.core.id, task);
        }

        for (Task task : tasks) {
            if (task.core.parent != null) {
                mappedTasks.get(task.core.parent).children.add(task);
            } else {
                taskTree.add(task);
            }
        }

        return taskTree;

    }

    public static List<Task> flatten(List<Task> tree) {
        List<Task> tasks = new ArrayList<>();
        for (Task task : tree) {
            tasks.add(task);
            tasks.addAll(flatten(task.children));
        }
        return tasks;
    }
}
