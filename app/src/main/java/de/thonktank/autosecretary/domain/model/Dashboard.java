package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Dashboard {
    public final int xp;
    public final List<Task> tasks;

    public Dashboard(int xp, List<Task> tasks) {
        this.xp = Math.max(0, xp);
        this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
    }
}
