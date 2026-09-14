package de.thonktank.autosecretary;

import java.time.LocalDate;
import java.util.*;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.presentation.today.*;

public final class FlowChainFixtures {
    public static FlowRunSummary run(String id, String title, FlowChainUiModel.Mode mode, long end) {
        List<String> ids = List.of(id + ":s", id + ":a", id + ":f");
        FlowTileGraph graph = new FlowTileGraph(ids, List.of(new FlowTileGraph.Link(ids.get(0), ids.get(1)),
                new FlowTileGraph.Link(ids.get(1), ids.get(2))));
        List<FlowGraphRun.Step> steps = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            FlowGraphRun.State state = i == 0 || mode == FlowChainUiModel.Mode.READY ? FlowGraphRun.State.DONE
                    : i == 2 ? FlowGraphRun.State.BLOCKED : mode == FlowChainUiModel.Mode.COUNTDOWN
                    ? FlowGraphRun.State.WAITING_TIME : mode == FlowChainUiModel.Mode.RESOURCE
                    ? FlowGraphRun.State.WAITING_RESOURCE : FlowGraphRun.State.AVAILABLE;
            boolean acted = state == FlowGraphRun.State.DONE || state == FlowGraphRun.State.WAITING_TIME;
            FlowGraphDefinition.Node source = new FlowGraphDefinition.Node("source:" + i,
                    i == 0 ? title : i == 1 ? "Aufhängen" : "Wegräumen", StepPrescription.forAmount(StepAmount.none()), "",
                    FlowDelayPolicy.fixed(i == 1 ? 3_600_000 : 0));
            steps.add(new FlowGraphRun.Step(ids.get(i), source, state, acted ? (i == 1 ? 3_600_000L : 0L) : null,
                    state == FlowGraphRun.State.WAITING_TIME ? end : null,
                    acted ? Math.max(0L, end - 3_600_000) : null, acted ? 10L : 0L));
        }
        FlowGraphRun run = new FlowGraphRun(id, TaskId.of("laundry"), ids.get(0), graph, steps, List.of(), 0, false);
        return new FlowRunSummary(new FlowGraphRunRecord(run, id, LocalDate.of(2026, 9, 14),
                TaskSlot.MORNING, 0, 0, 0, 0), "Wäsche", List.of());
    }

    public static FlowChainUiModel chain(String id, String title, FlowChainUiModel.Mode mode, long end) {
        TaskId task = TaskId.of("laundry");
        ComboProgress combo = new ComboProgress(ComboProgress.taskOwner(task), task, ComboProgress.Kind.TASK, 3, null);
        return new FlowChainUiModel(run(id, title, mode, end), Map.of(combo.ownerId, combo), new RewardTextFormatter(Locale.GERMANY));
    }
}
