package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Dashboard {
    public final int xp;
    public final List<DashboardTask> tasks;

    public Dashboard(int xp, List<DashboardTask> tasks) {
        this.xp = Math.max(0, xp);
        this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
    }
}
