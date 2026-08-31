package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingContext;
import de.thonktank.autosecretary.domain.model.TrainingHistoryEntry;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Builds the bounded, audit-ordered explainability projection for Today. */
public final class LoadTrainingContext {
    public static final int HISTORY_LIMIT = 10;
    private final TrainingRepository repository;

    public LoadTrainingContext(TrainingRepository repository) {
        this.repository = repository;
    }

    public TrainingContext execute(String templateId) {
        return repository.inTransaction(() -> load(templateId));
    }

    private TrainingContext load(String templateId) {
        TaskStepTemplate template = repository.findTemplate(templateId);
        if (template == null || template.assistantProfile == null) return null;
        TrainingAdjustment latest = repository.latestTrainingAdjustment(templateId);
        TrainingLoadRequest open = repository.openTrainingLoadRequest(templateId);
        List<TrainingAdjustment> adjustments = repository.recentTrainingAdjustments(
                templateId, HISTORY_LIMIT);
        List<TrainingLoadRequest> requests = repository.recentTrainingLoadRequests(
                templateId, HISTORY_LIMIT);
        List<TrainingHistoryEntry> history = new ArrayList<>();
        for (TrainingAdjustment value : adjustments)
            history.add(TrainingHistoryEntry.adjustment(value));
        for (TrainingLoadRequest value : requests)
            history.add(TrainingHistoryEntry.request(value));
        history.sort(Comparator.comparingLong((TrainingHistoryEntry value) -> value.auditOrder)
                .reversed());
        if (history.size() > HISTORY_LIMIT)
            history = new ArrayList<>(history.subList(0, HISTORY_LIMIT));
        long latestOrder = history.isEmpty() ? 0 : history.get(0).auditOrder;
        boolean current = latest != null
                && latest.state == TrainingAdjustment.State.APPLIED
                && latest.auditOrder == latestOrder
                && template.amount.equals(latest.after)
                && template.prescription.plannedLoad().equals(latest.afterLoad);
        return new TrainingContext(templateId, template.assistantProfile.state, open, latest,
                history, current);
    }
}
