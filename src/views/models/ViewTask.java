package views.models;

import java.util.List;
import java.util.ArrayList;

import database.task.*;

public class ViewTask {
    public Task task;
    public int indent;

    public ViewTask(Task task, int indent) {
        this.task = task;
        this.indent = indent;
    }

    public static List<ViewTask> fromTree(List<Task> tree, Integer indent) {
        List<ViewTask> viewTasks = new ArrayList<>();
        for (Task task : tree) {
            ViewTask viewTask = new ViewTask(task, indent);
            viewTasks.add(viewTask);
            viewTasks.addAll(fromTree(task.children, indent+1));
        }
        return viewTasks;
    }
}