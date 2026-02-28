package com.autosecretary.features.task.ui.list.state;

import com.autosecretary.features.task.application.listmodel.TaskListItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a TaskListItem with tree context for hierarchical rendering.
 * Maintains a depth value (set during tree sorting) for indentation in RecyclerView,
 * and a list of child ViewSlots for representing task or calendar hierarchies.
 */
public class ViewSlot {
    private final TaskListItem item;
    private int depth;

    private List<ViewSlot> children = new ArrayList<>();

    public ViewSlot(TaskListItem item) {
        this.item = item;
    }

    public TaskListItem getItem() {
        return item;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public List<ViewSlot> getChildren() {
        return children;
    }

    public void setChildren(List<ViewSlot> children) {
        this.children = children;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }
}
