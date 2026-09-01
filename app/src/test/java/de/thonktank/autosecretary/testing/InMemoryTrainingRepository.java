package de.thonktank.autosecretary.testing;

import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Small transactional double for the adaptive-training slice only. */
public final class InMemoryTrainingRepository implements TrainingRepository {
    public final StepRepository steps = adapter(StepRepository.class);
    public final TransactionRunner transactions = adapter(TransactionRunner.class);
    private Map<String, TaskStepTemplate> templates = new LinkedHashMap<>();
    private Map<String, TrainingAdjustment> adjustments = new LinkedHashMap<>();
    private Map<String, TrainingLoadRequest> requests = new LinkedHashMap<>();
    private double effectiveSets;

    private <T> T adapter(Class<T> port) {
        return port.cast(Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[]{port},
                (proxy, method, arguments) -> invokePort(port, method, arguments)));
    }

    private Object invokePort(Class<?> port, Method method, Object[] arguments) throws Throwable {
        try {
            return getClass().getMethod(method.getName(), method.getParameterTypes())
                    .invoke(this, arguments);
        } catch (NoSuchMethodException missing) {
            if (List.class.isAssignableFrom(method.getReturnType())) return new ArrayList<>();
            if (method.getReturnType() == void.class)
                throw new UnsupportedOperationException(port.getSimpleName() + '.'
                        + method.getName() + " is not supported by this test fixture");
            return null;
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    public void insertTemplate(TaskStepTemplate template) { templates.put(template.id, template); }

    public void setEffectiveSets(double value) { effectiveSets = value; }

    public <T> T inTransaction(TransactionRunner.Transaction<T> operation) {
        Map<String, TaskStepTemplate> beforeTemplates = new LinkedHashMap<>(templates);
        Map<String, TrainingAdjustment> beforeAdjustments = new LinkedHashMap<>(adjustments);
        Map<String, TrainingLoadRequest> beforeRequests = new LinkedHashMap<>(requests);
        try {
            return operation.execute();
        } catch (RuntimeException | Error failure) {
            templates = beforeTemplates;
            adjustments = beforeAdjustments;
            requests = beforeRequests;
            throw failure;
        }
    }

    public TaskStepTemplate findTemplate(String id) { return templates.get(id); }

    public void updateTemplate(TaskStepTemplate template) {
        templates.put(template.id, template);
    }

    @Override public double effectiveSetsSince(TrainingMuscleGroup muscle, LocalDate start,
                                               LocalDate end) {
        return effectiveSets;
    }

    @Override public void insertTrainingAdjustment(TrainingAdjustment adjustment) {
        adjustments.put(adjustment.id, adjustment);
    }

    @Override public TrainingAdjustment latestTrainingAdjustment(String templateId) {
        List<TrainingAdjustment> values = recentTrainingAdjustments(templateId, 1);
        return values.isEmpty() ? null : values.get(0);
    }

    @Override public List<TrainingAdjustment> recentTrainingAdjustments(String templateId,
                                                                        int limit) {
        List<TrainingAdjustment> values = new ArrayList<>();
        for (TrainingAdjustment value : adjustments.values())
            if (value.templateId.equals(templateId)) values.add(value);
        values.sort(Comparator.comparingLong((TrainingAdjustment value) -> value.auditOrder)
                .reversed());
        return values.size() <= limit ? values : new ArrayList<>(values.subList(0, limit));
    }

    @Override public void updateTrainingAdjustment(TrainingAdjustment adjustment) {
        adjustments.put(adjustment.id, adjustment);
    }

    @Override public long nextTrainingAuditOrder() {
        long result = 0;
        for (TrainingAdjustment value : adjustments.values())
            result = Math.max(result, value.auditOrder);
        for (TrainingLoadRequest value : requests.values())
            result = Math.max(result, value.auditOrder);
        return result + 1;
    }

    @Override public void insertTrainingLoadRequest(TrainingLoadRequest request) {
        requests.put(request.id, request);
    }

    @Override public TrainingLoadRequest openTrainingLoadRequest(String templateId) {
        TrainingLoadRequest result = null;
        for (TrainingLoadRequest value : requests.values())
            if (value.templateId.equals(templateId)
                    && value.state == TrainingLoadRequest.State.OPEN
                    && (result == null || value.auditOrder > result.auditOrder)) result = value;
        return result;
    }

    @Override public List<TrainingLoadRequest> recentTrainingLoadRequests(String templateId,
                                                                          int limit) {
        List<TrainingLoadRequest> values = new ArrayList<>();
        for (TrainingLoadRequest value : requests.values())
            if (value.templateId.equals(templateId)) values.add(value);
        values.sort(Comparator.comparingLong((TrainingLoadRequest value) -> value.auditOrder)
                .reversed());
        return values.size() <= limit ? values : new ArrayList<>(values.subList(0, limit));
    }

    @Override public void updateTrainingLoadRequest(TrainingLoadRequest request) {
        requests.put(request.id, request);
    }
}
