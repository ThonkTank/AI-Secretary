package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.AgentTaskChangeResult;
import de.thonktank.autosecretary.domain.model.AgentTaskChangeSet;
import de.thonktank.autosecretary.domain.model.AgentTaskOperation;
import de.thonktank.autosecretary.domain.model.AgentTaskSnapshot;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskKind;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

import java.util.ArrayList;
import java.util.List;

/** Applies one confirmed agent proposal atomically through the normal task use cases. */
public final class ApplyAgentTaskChangeSet {
    private final CatalogRepository catalog;
    private final TransactionRunner transactions;
    private final CreateTask create;
    private final UpdateTask update;
    private final LoadAgentTaskCatalog agentCatalog;

    public ApplyAgentTaskChangeSet(CatalogRepository catalog, TransactionRunner transactions,
                                   CreateTask create, UpdateTask update,
                                   LoadAgentTaskCatalog agentCatalog) {
        if (catalog == null || transactions == null || create == null || update == null
                || agentCatalog == null)
            throw new IllegalArgumentException("Agent task dependencies are required");
        this.catalog = catalog;
        this.transactions = transactions;
        this.create = create;
        this.update = update;
        this.agentCatalog = agentCatalog;
    }

    public AgentTaskChangeResult execute(AgentTaskChangeSet changeSet) {
        if (changeSet == null) return AgentTaskChangeResult.rejected("Vorschlag fehlt.");
        try {
            return transactions.inTransaction(() -> applyInsideTransaction(changeSet));
        } catch (IllegalArgumentException invalid) {
            return AgentTaskChangeResult.rejected(invalid.getMessage());
        } catch (RuntimeException failure) {
            return AgentTaskChangeResult.rejected("Änderungen konnten nicht gespeichert werden.");
        }
    }

    private AgentTaskChangeResult applyInsideTransaction(AgentTaskChangeSet changeSet) {
        for (AgentTaskOperation operation : changeSet.operations) {
            if (operation.kind == AgentTaskOperation.Kind.CREATE) continue;
            Task current = catalog.findTask(operation.taskId);
            if (current == null)
                return AgentTaskChangeResult.stale("Eine Aufgabe existiert nicht mehr.");
            if (current.kind != TaskKind.TASK)
                return AgentTaskChangeResult.rejected("Abläufe können nicht im Chat geändert werden.");
            AgentTaskSnapshot snapshot = agentCatalog.find(operation.taskId);
            if (snapshot == null || !snapshot.fingerprint.equals(operation.expectedFingerprint))
                return AgentTaskChangeResult.stale("Eine Aufgabe wurde seit dem Vorschlag geändert.");
            if (operation.kind == AgentTaskOperation.Kind.UPDATE && snapshot.archived)
                return AgentTaskChangeResult.rejected("Archivierte Aufgaben sind nur lesbar.");
        }

        List<TaskId> created = new ArrayList<>();
        for (AgentTaskOperation operation : changeSet.operations) {
            if (operation.kind == AgentTaskOperation.Kind.CREATE)
                created.add(create.executeInsideTransaction(operation.definition));
            else if (operation.kind == AgentTaskOperation.Kind.UPDATE)
                update.executeInsideTransaction(operation.taskId, operation.definition, true);
            else catalog.deleteTask(operation.taskId);
        }
        return AgentTaskChangeResult.applied(created);
    }
}
