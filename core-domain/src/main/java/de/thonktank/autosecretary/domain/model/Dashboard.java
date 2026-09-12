package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public final class Dashboard {
    public final List<TodayPlacement> todayPlacements;
    public final int xp;
    public final List<DashboardTask> tasks;
    public final Map<String, ComboProgress> combos;
    public final List<FlowRunSummary> flowRuns;
    public final List<FlowTaskSheet> flowTaskSheets;
    public final Map<String, TrainingContext> trainingContexts;

    public Dashboard(int xp, List<DashboardTask> tasks) {
        this(xp, tasks, Collections.emptyMap());
    }

    public Dashboard(int xp, List<DashboardTask> tasks, Map<String, ComboProgress> combos) {
        this(xp, tasks, combos, Collections.emptyList());
    }

    public Dashboard(int xp, List<DashboardTask> tasks, Map<String, ComboProgress> combos,
                     List<FlowRunSummary> flowRuns) {
        this(xp, tasks, combos, flowRuns, Collections.emptyList(), Collections.emptyMap());
    }

    public Dashboard(int xp, List<DashboardTask> tasks, Map<String, ComboProgress> combos,
                     List<FlowRunSummary> flowRuns,
                     Map<String, TrainingContext> trainingContexts) {
        this(xp, tasks, combos, flowRuns, Collections.emptyList(), trainingContexts);
    }

    public Dashboard(int xp, List<DashboardTask> tasks, Map<String, ComboProgress> combos,
                     List<FlowRunSummary> flowRuns, List<FlowTaskSheet> flowTaskSheets,
                     Map<String, TrainingContext> trainingContexts) {
        this(xp, tasks, combos, flowRuns, flowTaskSheets, trainingContexts, Collections.emptyList());
    }

    public Dashboard(int xp, List<DashboardTask> tasks, Map<String, ComboProgress> combos,
                     List<FlowRunSummary> flowRuns, List<FlowTaskSheet> flowTaskSheets,
                     Map<String, TrainingContext> trainingContexts, List<TodayPlacement> placements) {
        this.todayPlacements = Collections.unmodifiableList(new ArrayList<>(placements));
        this.xp = Math.max(0, xp);
        this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
        this.combos = Collections.unmodifiableMap(new LinkedHashMap<>(combos));
        this.flowRuns = Collections.unmodifiableList(new ArrayList<>(flowRuns));
        this.flowTaskSheets = Collections.unmodifiableList(new ArrayList<>(flowTaskSheets));
        this.trainingContexts = Collections.unmodifiableMap(
                new LinkedHashMap<>(trainingContexts));
    }
}
