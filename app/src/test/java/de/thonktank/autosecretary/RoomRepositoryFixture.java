package de.thonktank.autosecretary;

import de.thonktank.autosecretary.data.local.RoomCatalogRepository;
import de.thonktank.autosecretary.data.local.RoomFlowRepository;
import de.thonktank.autosecretary.data.local.RoomStepRepository;
import de.thonktank.autosecretary.data.local.RoomTodayRepository;
import de.thonktank.autosecretary.data.local.RoomTrainingRepository;
import de.thonktank.autosecretary.data.local.RoomTransactionRunner;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.FlowRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Composes the five real Room adapters for cross-slice integration tests. */
final class RoomRepositoryFixture {
    final CatalogRepository catalog;
    final StepRepository steps;
    final TodayRepository today;
    final FlowRepository flows;
    final TrainingRepository training;
    final TransactionRunner transactions;
    final de.thonktank.autosecretary.domain.repository.FlowGraphDefinitionRepository graphDefinitions;
    final de.thonktank.autosecretary.domain.repository.FlowGraphRunRepository graphRuns;

    RoomRepositoryFixture(AppDatabase database) {
        transactions = new RoomTransactionRunner(database);
        catalog = new RoomCatalogRepository(database);
        steps = new RoomStepRepository(database, transactions);
        today = new RoomTodayRepository(database);
        flows = new RoomFlowRepository(database);
        training = new RoomTrainingRepository(database);
        graphDefinitions = new de.thonktank.autosecretary.data.local.SqlFlowGraphDefinitionRepository(
                () -> database.getOpenHelper().getWritableDatabase(), steps);
        graphRuns = new de.thonktank.autosecretary.data.local.SqlFlowGraphRunRepository(
                () -> database.getOpenHelper().getWritableDatabase());
    }

    de.thonktank.autosecretary.domain.usecase.LoadDashboard dashboard() {
        return new de.thonktank.autosecretary.domain.usecase.LoadDashboard(catalog, steps, today,
                flows, training, transactions, new de.thonktank.autosecretary.domain.usecase.LoadGraphFlowSheets(
                catalog, steps, today, flows, graphDefinitions, graphRuns, transactions));
    }

    /** Historical linear definitions are expressed as graph snapshots, never legacy run rows. */
    void graphFromLinear(de.thonktank.autosecretary.domain.model.TaskId taskId) {
        transactions.inTransaction(() -> {
            var nodes = new java.util.ArrayList<de.thonktank.autosecretary.domain.model.FlowGraphDefinition.Node>();
            var links = new java.util.ArrayList<de.thonktank.autosecretary.domain.model.FlowTileGraph.Link>();
            var leases = new java.util.ArrayList<de.thonktank.autosecretary.domain.model.FlowGraphDefinition.Lease>();
            var waits = new java.util.HashMap<String, de.thonktank.autosecretary.domain.model.FlowDelayPolicy>();
            for (var link : flows.stepTransitions(taskId)) {
                links.add(new de.thonktank.autosecretary.domain.model.FlowTileGraph.Link(link.sourceStepId, link.targetStepId));
                waits.put(link.sourceStepId, link.delay);
            }
            for (var step : steps.templates(taskId)) nodes.add(new de.thonktank.autosecretary.domain.model.FlowGraphDefinition.Node(
                    step.id, step.text, step.prescription, step.note,
                    waits.getOrDefault(step.id, de.thonktank.autosecretary.domain.model.FlowDelayPolicy.fixed(0))));
            for (var lease : flows.stepResourceLeases(taskId)) leases.add(new de.thonktank.autosecretary.domain.model.FlowGraphDefinition.Lease(
                    lease.id, lease.resourceId, lease.acquireStepId, lease.releaseStepId, lease.units, false));
            graphDefinitions.replace(new de.thonktank.autosecretary.domain.model.FlowGraphDefinition(taskId,
                    new de.thonktank.autosecretary.domain.model.FlowTileGraph(nodes.stream().map(node -> node.id).toList(), links), nodes, leases));
            return null;
        });
    }
}
