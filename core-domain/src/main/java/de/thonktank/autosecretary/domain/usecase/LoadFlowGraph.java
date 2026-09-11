package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.*;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import java.util.*;

/** Loads one coherent editor revision, including the shared capacity catalog. */
public final class LoadFlowGraph {
    private final CatalogRepository tasks;
    private final StepRepository steps;
    private final FlowRepository resources;
    private final FlowGraphDefinitionRepository graphs;
    private final TransactionRunner transactions;

    public LoadFlowGraph(CatalogRepository tasks, StepRepository steps, FlowRepository resources,
                         FlowGraphDefinitionRepository graphs, TransactionRunner transactions) {
        this.tasks = tasks; this.steps = steps; this.resources = resources;
        this.graphs = graphs; this.transactions = transactions;
    }

    public Setup execute(TaskId id) {
        return transactions.inTransaction(() -> {
            if (id == null) return new Setup(null, Collections.emptyList(), null, resources.capacityResources());
            Task task = tasks.findTask(id);
            FlowGraphDefinition definition = task == null ? null : graphs.find(id);
            if (task == null || definition == null) throw new IllegalArgumentException("Ablauf existiert nicht mehr");
            return new Setup(task.withKind(TaskKind.FLOW), steps.templates(id), definition, resources.capacityResources());
        });
    }

    public static final class Setup {
        public final Task task;
        public final List<TaskStepTemplate> steps;
        public final FlowGraphDefinition definition;
        public final List<CapacityResource> resources;

        private Setup(Task task, List<TaskStepTemplate> steps, FlowGraphDefinition definition, List<CapacityResource> resources) {
            this.task = task;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
            this.definition = definition;
            this.resources = Collections.unmodifiableList(new ArrayList<>(resources));
        }
    }
}
