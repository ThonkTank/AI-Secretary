package de.thonktank.autosecretary.presentation.today;

import java.util.*;
import de.thonktank.autosecretary.domain.model.*;

/** One begun execution, independent of which branch currently offers an action. */
public final class FlowChainUiModel {
    public enum Mode { TAU, COUNTDOWN, RESOURCE, READY }
    public final String runId;
    public final String title;
    public final List<String> stepIds;
    public final Mode mode;
    public final XpVesselUiModel vessel;
    public final Long waitStartedAt;
    public final Long readyAt;
    public final List<FlowWaitUiModel> editableWaits;

    public FlowChainUiModel(FlowRunSummary run, Map<String, ComboProgress> combos,
                           RewardTextFormatter formatter) {
        runId = run.id; title = run.seedTitle;
        List<String> identities = new ArrayList<>();
        for (FlowRunSummary.Step step : run.steps) identities.add(step.id);
        stepIds = Collections.unmodifiableList(identities);
        boolean available = false;
        FlowRunSummary.Step next = null;
        long earned = 0, future = 0;
        int done = 0;
        List<FlowWaitUiModel> waits = new ArrayList<>();
        for (FlowRunSummary.Step step : run.steps) {
            available |= step.state == FlowGraphRun.State.AVAILABLE;
            earned = Math.addExact(earned, step.earnedTau);
            if (step.actionAtEpochMillis != null) done++;
            else future = Math.addExact(future, Math.max(0, RewardPolicy.step(
                    combos.get(ComboProgress.stepOwner(step.sourceTemplateId))).resultXp - step.earnedTau));
            if (step.readyAtEpochMillis != null && (next == null
                    || step.readyAtEpochMillis < next.readyAtEpochMillis)) next = step;
            if (step.canAdjustWait) waits.add(new FlowWaitUiModel(run.id, step.waitId,
                    step.title, step.readyAtEpochMillis, step.state == FlowGraphRun.State.DONE));
        }
        mode = run.collectionAvailable ? Mode.READY : available ? Mode.TAU
                : next != null ? Mode.COUNTDOWN : Mode.RESOURCE;
        readyAt = next == null ? null : next.readyAtEpochMillis;
        waitStartedAt = next == null ? null : next.actionAtEpochMillis;
        editableWaits = Collections.unmodifiableList(waits);
        RewardBreakdown reward = RewardPolicy.routine(Math.toIntExact(run.uncollectedTau),
                combos.get(ComboProgress.taskOwner(run.taskId)));
        vessel = XpVesselUiModel.quantitative(reward, done, run.totalSteps,
                Math.toIntExact(earned), Math.toIntExact(Math.addExact(earned, future)),
                run.collectionAvailable, formatter);
    }
}
