package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validated, immutable definition graph for one task. Runtime paths are always linear. */
public final class StepFlowDefinition {
    public final TaskId taskId;
    public final List<TaskStepTemplate> steps;
    public final List<StepTransition> transitions;
    public final List<StepResourceLease> resourceLeases;
    public final List<CapacityResource> resources;

    private final Map<String, TaskStepTemplate> stepById;
    private final Map<String, StepTransition> transitionBySource;

    public StepFlowDefinition(TaskId taskId, List<TaskStepTemplate> steps,
                              List<StepTransition> transitions,
                              List<StepResourceLease> resourceLeases,
                              List<CapacityResource> resources) {
        if (taskId == null || steps == null || transitions == null || resourceLeases == null
                || resources == null)
            throw new FlowDefinitionException("Ablaufdefinition ist unvollständig");
        this.taskId = taskId;
        this.steps = immutable(steps);
        this.transitions = immutable(transitions);
        this.resourceLeases = immutable(resourceLeases);
        this.resources = immutable(resources);
        this.stepById = indexSteps();
        this.transitionBySource = validateAndIndexTransitions();
        validateFollowUps();
        validateAcyclic();
        validateLeases();
    }

    public boolean participates(String stepId) {
        if (transitionBySource.containsKey(stepId)) return true;
        for (StepTransition transition : transitions)
            if (transition.targetStepId.equals(stepId)) return true;
        for (StepResourceLease lease : resourceLeases)
            if (lease.acquireStepId.equals(stepId) || lease.releaseStepId.equals(stepId)) return true;
        return false;
    }

    public List<TaskStepTemplate> resolvedPath(String seedStepId) {
        TaskStepTemplate seed = stepById.get(seedStepId);
        if (seed == null) throw new FlowDefinitionException("Startschritt existiert nicht");
        if (seed.activationKind != StepActivationKind.SCHEDULED)
            throw new FlowDefinitionException("Nur ein geplanter Schritt kann einen Lauf starten");
        List<TaskStepTemplate> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String current = seedStepId;
        while (current != null) {
            if (!visited.add(current))
                throw new FlowDefinitionException("Ablauf enthält einen Kreis");
            result.add(stepById.get(current));
            StepTransition next = transitionBySource.get(current);
            current = next == null ? null : next.targetStepId;
        }
        return Collections.unmodifiableList(result);
    }

    public StepTransition transitionAfter(String stepId) {
        return transitionBySource.get(stepId);
    }

    public List<StepResourceLease> leasesForPath(List<TaskStepTemplate> path) {
        Set<String> ids = new HashSet<>();
        for (TaskStepTemplate step : path) ids.add(step.id);
        List<StepResourceLease> result = new ArrayList<>();
        for (StepResourceLease lease : resourceLeases)
            if (ids.contains(lease.acquireStepId)) result.add(lease);
        return Collections.unmodifiableList(result);
    }

    private Map<String, TaskStepTemplate> indexSteps() {
        Map<String, TaskStepTemplate> result = new LinkedHashMap<>();
        for (TaskStepTemplate step : steps) {
            if (!taskId.equals(step.taskId))
                throw new FlowDefinitionException("Ablaufschritte müssen zur selben Aufgabe gehören");
            if (result.put(step.id, step) != null)
                throw new FlowDefinitionException("Schritt-ID ist doppelt: " + step.id);
            if (step.activationKind == StepActivationKind.FOLLOW_UP
                    && (step.weekdayMask != 0 || step.intervalDays != 0))
                throw new FlowDefinitionException(
                        "Ein Folgeschritt darf keinen eigenen Rhythmus haben: " + step.text);
        }
        return result;
    }

    private Map<String, StepTransition> validateAndIndexTransitions() {
        Map<String, StepTransition> result = new LinkedHashMap<>();
        for (StepTransition transition : transitions) {
            TaskStepTemplate source = stepById.get(transition.sourceStepId);
            TaskStepTemplate target = stepById.get(transition.targetStepId);
            if (source == null || target == null)
                throw new FlowDefinitionException("Ablauf verweist auf einen fremden Schritt");
            if (target.activationKind != StepActivationKind.FOLLOW_UP)
                throw new FlowDefinitionException(
                        "Ein geplanter Schritt darf kein Folgeschritt sein: " + target.text);
            if (result.put(transition.sourceStepId, transition) != null)
                throw new FlowDefinitionException(
                        "Ein Schritt darf höchstens einen direkten Nachfolger haben: " + source.text);
        }
        return result;
    }

    private void validateFollowUps() {
        Set<String> targets = new HashSet<>();
        for (StepTransition transition : transitions) targets.add(transition.targetStepId);
        for (TaskStepTemplate step : steps)
            if (step.activationKind == StepActivationKind.FOLLOW_UP && !targets.contains(step.id))
                throw new FlowDefinitionException(
                        "Folgeschritt ist nicht erreichbar: " + step.text);
    }

    private void validateAcyclic() {
        Map<String, Integer> color = new HashMap<>();
        for (TaskStepTemplate step : steps) visit(step.id, color);
    }

    private void visit(String id, Map<String, Integer> color) {
        int state = color.getOrDefault(id, 0);
        if (state == 1) throw new FlowDefinitionException("Ablauf enthält einen Kreis");
        if (state == 2) return;
        color.put(id, 1);
        StepTransition transition = transitionBySource.get(id);
        if (transition != null) visit(transition.targetStepId, color);
        color.put(id, 2);
    }

    private void validateLeases() {
        Map<String, CapacityResource> resourceById = new HashMap<>();
        Set<String> names = new HashSet<>();
        for (CapacityResource resource : resources) {
            if (resourceById.put(resource.id, resource) != null)
                throw new FlowDefinitionException("Ressourcen-ID ist doppelt: " + resource.id);
            if (!names.add(resource.normalizedName))
                throw new FlowDefinitionException("Ressourcenname ist doppelt: " + resource.name);
        }
        Set<String> leaseIds = new HashSet<>();
        for (StepResourceLease lease : resourceLeases) {
            if (!taskId.equals(lease.taskId))
                throw new FlowDefinitionException("Ressourcenbindung gehört zu einer anderen Aufgabe");
            if (!leaseIds.add(lease.id))
                throw new FlowDefinitionException("Ressourcenbindung ist doppelt: " + lease.id);
            TaskStepTemplate acquire = stepById.get(lease.acquireStepId);
            TaskStepTemplate release = stepById.get(lease.releaseStepId);
            CapacityResource resource = resourceById.get(lease.resourceId);
            if (acquire == null || release == null || resource == null)
                throw new FlowDefinitionException("Ressourcenbindung verweist auf ein fehlendes Element");
            if (lease.units > resource.capacity)
                throw new FlowDefinitionException("Ressourcenbindung überschreitet die Kapazität von "
                        + resource.name);
            String cursor = lease.acquireStepId;
            boolean reached = false;
            while (transitionBySource.containsKey(cursor)) {
                cursor = transitionBySource.get(cursor).targetStepId;
                if (cursor.equals(lease.releaseStepId)) {
                    reached = true;
                    break;
                }
            }
            if (!reached)
                throw new FlowDefinitionException("Freigabeschritt muss nach dem Belegungsschritt "
                        + "erreichbar sein: " + acquire.text);
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
