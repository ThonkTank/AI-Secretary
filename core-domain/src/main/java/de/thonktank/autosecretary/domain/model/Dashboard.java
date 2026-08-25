package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public final class Dashboard {
    public final int xp;
    public final List<DashboardTask> tasks;
    public final Map<String, ComboProgress> combos;
    public final List<FlowRunSummary> flowRuns;

    public Dashboard(int xp, List<DashboardTask> tasks) {
        this(xp, tasks, Collections.emptyMap());
    }

    public Dashboard(int xp, List<DashboardTask> tasks, Map<String, ComboProgress> combos) {
        this(xp, tasks, combos, Collections.emptyList());
    }

    public Dashboard(int xp, List<DashboardTask> tasks, Map<String, ComboProgress> combos,
                     List<FlowRunSummary> flowRuns) {
        this.xp = Math.max(0, xp);
        this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
        this.combos = Collections.unmodifiableMap(new LinkedHashMap<>(combos));
        this.flowRuns = Collections.unmodifiableList(new ArrayList<>(flowRuns));
    }
}
