package com.autosecretary.features.task.ui.list.state;

import com.autosecretary.features.task.application.listmodel.TaskListItem;

import java.util.ArrayList;
import java.util.List;

public class ViewSlot {
    public final TaskListItem item;
    private int depth;

    private List<ViewSlot> children = new ArrayList<>();

    public ViewSlot(TaskListItem item) {
        this.item = item;
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
