package de.thonktank.autosecretary;

import java.util.List;

public final class DashboardState {
    public final int xp;
    public final List<TaskSnapshot> tasks;
    DashboardState(int xp, List<TaskSnapshot> tasks) { this.xp = xp; this.tasks = tasks; }
}
