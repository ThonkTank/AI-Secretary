package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayCommand;
import de.thonktank.autosecretary.presentation.today.TodayCoordinator;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;

public final class TodayCoordinatorTest {
    @Test public void immediateMoveProducesExactlyOnePersistenceCommand() {
        List<TodayCommand> commands = new ArrayList<>();
        TodayCoordinator coordinator = new TodayCoordinator(today(), commands::add, state -> { });

        coordinator.emit(TodayAction.moveStep("a", null));
        coordinator.emit(TodayAction.moveStep("a", null));

        assertEquals(1, commands.size());
        assertEquals(TodayCommand.Kind.PERSIST_REORDER, commands.get(0).kind);
        assertEquals("a", commands.get(0).id);
    }

    @Test public void chosenFlowDelayCrossesTheClosedTodayCommandBoundary() {
        List<TodayCommand> commands = new ArrayList<>();
        TodayCoordinator coordinator = new TodayCoordinator(today(), commands::add, state -> { });

        coordinator.emit(TodayAction.toggleStep("a", 7_200_000L));

        assertEquals(1, commands.size());
        assertEquals(TodayCommand.Kind.TOGGLE_STEP_WITH_DELAY, commands.get(0).kind);
        assertEquals("a", commands.get(0).id);
        assertEquals(7_200_000L, commands.get(0).longValue);
    }

    @Test public void selectingAVisibleStepPublishesLocallyWithoutACommand() {
        List<TodayCommand> commands = new ArrayList<>();
        List<de.thonktank.autosecretary.presentation.today.TodayFeatureState> states =
                new ArrayList<>();
        TodayCoordinator coordinator = new TodayCoordinator(today(), commands::add, states::add);

        coordinator.emit(TodayAction.selectStep("c"));

        assertTrue(commands.isEmpty());
        assertEquals(1, states.size());
        assertEquals("c", states.get(0).selectedStepId);
        assertEquals("c", states.get(0).focus.rows.get(0).id());
    }

    @Test public void everyCommandActionEmitsExactlyOnceAndReturnsWithItsPayload() {
        var target = today().focus.actionTarget;
        List<TodayAction> actions = Arrays.asList(
                TodayAction.completeOccurrence("occ"), TodayAction.requestClose("task", "Titel"),
                TodayAction.completeRemaining("occ"), TodayAction.harvest("occ"),
                TodayAction.defer(target), TodayAction.bringFirst(target),
                TodayAction.startFlowCandidate("candidate"),
                TodayAction.startFlowCandidate("candidate", 72_000L),
                TodayAction.completeFlowStep("runtime"),
                TodayAction.completeFlowStep("runtime", 73_000L),
                TodayAction.collectFlow("run"), TodayAction.adjustFlowWait("run", "wait", 74_000L),
                TodayAction.toggleStep("step"), TodayAction.toggleStep("step", 75_000L),
                TodayAction.finishStep("step"), TodayAction.advanceStep("step"),
                TodayAction.undoOccurrence("occ"), TodayAction.adjustRepetition("step", 2),
                TodayAction.adjustTrainingLoad("step", 500), TodayAction.adjustTrainingRir("step", -1),
                TodayAction.toggleTrainingSafety("step"), TodayAction.editRepetition("step", 3),
                TodayAction.submitRepetition("step"), TodayAction.startDurationTimer("step", "Timer", 90),
                TodayAction.pauseTimer("timer"), TodayAction.resumeTimer("timer"),
                TodayAction.resetTimer("timer"), TodayAction.observeTimer("timer"));
        EnumSet<TodayCommand.Kind> covered = EnumSet.noneOf(TodayCommand.Kind.class);
        for (TodayAction action : actions) {
            List<TodayCommand> commands = new ArrayList<>();
            TodayCoordinator coordinator = new TodayCoordinator(today(), commands::add, state -> { });
            coordinator.emit(action);
            assertEquals(action.kind.name(), 1, commands.size());
            TodayCommand command = commands.get(0);
            assertEquals(action.kind.name(), command.kind.name());
            assertEquals(action.id, command.id);
            assertEquals(action.relatedId, command.relatedId);
            assertEquals(action.text, command.text);
            assertEquals(action.value, command.value);
            assertEquals(action.longValue, command.longValue);
            assertEquals(action.target == null ? null : action.target.item, command.itemTarget);
            assertTrue("Duplicate command coverage: " + command.kind, covered.add(command.kind));
        }
        EnumSet<TodayCommand.Kind> expected = EnumSet.allOf(TodayCommand.Kind.class);
        // Reordering has a stateful, idempotent protocol covered by immediateMoveProducesExactlyOnePersistenceCommand.
        expected.remove(TodayCommand.Kind.PERSIST_REORDER);
        assertEquals(expected, covered);
    }

    @Test public void screenOwnerAndCoreDispatcherContainEveryActionKind() throws Exception {
        String coordinator = new String(Files.readAllBytes(Path.of(
                "../today-core/src/main/java/de/thonktank/autosecretary/presentation/today/TodayCoordinator.java")),
                StandardCharsets.UTF_8);
        String owner = new String(Files.readAllBytes(Path.of(
                "src/main/java/de/thonktank/autosecretary/presentation/today/TodayViewModel.java")),
                StandardCharsets.UTF_8);
        EnumSet<TodayAction.Kind> handled = EnumSet.noneOf(TodayAction.Kind.class);
        for (TodayAction.Kind kind : TodayAction.Kind.values())
            if (coordinator.contains("case " + kind.name() + ":")
                    || owner.contains("case " + kind.name() + ":")) handled.add(kind);

        assertEquals(EnumSet.allOf(TodayAction.Kind.class), handled);
        assertTrue(coordinator.contains("Unhandled Today action"));
        assertTrue(owner.contains("todayCoordinator.emit(action)"));
    }

    private static TodayUiModel today() {
        FocusTaskUiModel focus = FocusTaskFixtures.task("task", "Task")
                .occurrence("occurrence").steps(Arrays.asList(
                        FocusTaskFixtures.simpleStep("a", "A", false),
                        FocusTaskFixtures.simpleStep("b", "B", false),
                        FocusTaskFixtures.simpleStep("c", "C", false))).build();
        return new TodayUiModel(new XpProgress(0), focus, Collections.emptyList(),
                Collections.emptyList());
    }
}
