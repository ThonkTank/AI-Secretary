package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.schedule.TaskScheduleService;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.ScheduleCalculator;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepFlowDefinition;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.FlowRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

public final class UpdateTask {
    private final CatalogRepository catalog;
    private final StepRepository steps;
    private final TodayRepository today;
    private final FlowRepository flows;
    private final TrainingRepository training;
    private final TransactionRunner transactions;
    private final IdGenerator ids;
    private final Clock clock;

    public UpdateTask(CatalogRepository catalog, StepRepository steps, TodayRepository today,
                      FlowRepository flows, TrainingRepository training,
                      TransactionRunner transactions, IdGenerator ids, Clock clock) {
        this.catalog = catalog;
        this.steps = steps;
        this.today = today;
        this.flows = flows;
        this.training = training;
        this.transactions = transactions;
        this.ids = ids;
        this.clock = clock;
    }

    public void execute(TaskId id, TaskDefinition definition) {
        transactions.inTransaction(() -> {
            executeInsideTransaction(id, definition, true);
            return null;
        });
    }

    void executeInsideTransaction(TaskId id, TaskDefinition definition,
                                  boolean validateFlow) {
        Task current = catalog.findTask(id);
        if (current == null) return;
        LocalDate nextDue = current.nextDueOn;
        if (current.recurrence != definition.recurrence
                || current.weekdayMask != definition.weekdayMask
                || current.intervalDays != definition.intervalDays) {
            LocalDate today = clock == null ? current.nextDueOn : clock.today();
            if (today != null)
                nextDue = ScheduleCalculator.firstDue(definition.recurrence,
                        definition.weekdayMask, today);
        }
        catalog.updateTask(current.editDefinition(definition, current.catalogOrder, nextDue));
        syncTemplates(id, definition.steps);
        if (validateFlow) validateRetainedFlow(id);
        new TaskScheduleService(catalog, today, transactions, ids).sync(
                catalog.findTask(id), definition);
    }

    private void validateRetainedFlow(TaskId taskId) {
        if (flows == null) return;
        List<TaskStepTemplate> templates = steps.templates(taskId);
        boolean hasFollowUp = false;
        for (TaskStepTemplate template : templates)
            if (template.activationKind == StepActivationKind.FOLLOW_UP) {
                hasFollowUp = true;
                break;
            }
        List<de.thonktank.autosecretary.domain.model.StepTransition> transitions =
                flows.stepTransitions(taskId);
        List<de.thonktank.autosecretary.domain.model.StepResourceLease> leases =
                flows.stepResourceLeases(taskId);
        if (!hasFollowUp && transitions.isEmpty() && leases.isEmpty()) return;
        new StepFlowDefinition(taskId, templates, transitions, leases,
                flows.capacityResources());
    }

    private void syncTemplates(TaskId taskId, List<TaskStepDefinition> definitions) {
        List<TaskStepTemplate> existing = steps.templates(taskId);
        Set<String> existingIds = new HashSet<>();
        for (TaskStepTemplate value : existing) existingIds.add(value.id);
        Set<String> retained = new HashSet<>();
        List<TaskStepTemplate> updated = new ArrayList<>();
        for (int i = 0; i < definitions.size(); i++) {
            TaskStepDefinition step = definitions.get(i);
            String identity = step.id != null && existingIds.contains(step.id)
                    ? step.id : ids.nextId();
            TaskStepTemplate old = null;
            for (TaskStepTemplate candidate : existing)
                if (candidate.id.equals(identity)) { old = candidate; break; }
            de.thonktank.autosecretary.domain.model.TrainingAssistantProfile profile = null;
            if (step.assistantPolicy != null) {
                de.thonktank.autosecretary.domain.model.TrainingAssistantState state =
                        old != null && old.assistantProfile != null
                                && old.assistantProfile.policy.equals(step.assistantPolicy)
                                && old.prescription.equals(step.prescription)
                                ? old.assistantProfile.state
                                : de.thonktank.autosecretary.domain.model.TrainingAssistantState.calibrating();
                profile = new de.thonktank.autosecretary.domain.model.TrainingAssistantProfile(
                        step.assistantPolicy, state);
            }
            if (old != null && old.assistantProfile != null
                    && (!old.prescription.equals(step.prescription)
                    || !java.util.Objects.equals(old.assistantProfile.policy,
                    step.assistantPolicy))
                    && training != null && clock != null) {
                TrainingLoadRequest request = training.openTrainingLoadRequest(identity);
                if (request != null) training.updateTrainingLoadRequest(request.cancel(
                        TrainingLoadRequest.Resolution.MANUAL_CHANGE, clock.today()));
            }
            retained.add(identity);
            updated.add(new TaskStepTemplate(identity, taskId, i, step.text,
                    step.weekdayMask, step.intervalDays, step.prescription, profile, step.note,
                    step.activationKind));
        }
        for (TaskStepTemplate old : existing)
            if (!retained.contains(old.id)) steps.deleteTemplate(old.id);
        steps.insertTemplates(updated);
    }
}
