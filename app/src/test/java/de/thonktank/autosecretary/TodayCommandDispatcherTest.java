package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayCommand;
import de.thonktank.autosecretary.presentation.today.TodayCommandDispatcher;
import de.thonktank.autosecretary.presentation.today.TodayCoordinator;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;

/** Handler/payload contract; persistent effects are covered by TodayActionIntegrationRobolectricTest. */
public final class TodayCommandDispatcherTest {
    @Test public void everyCommandReachesExactlyItsHandlerWithTheOriginalPayloadAndReturns() {
        var focus = FocusTaskFixtures.task("task", "Task").occurrence("occurrence")
                .steps(Arrays.asList(FocusTaskFixtures.simpleStep("a", "A", false),
                        FocusTaskFixtures.simpleStep("b", "B", false))).build();
        var today = new TodayUiModel(new XpProgress(0), focus,
                Collections.emptyList(), Collections.emptyList());
        var target = focus.actionTarget;
        List<Case> cases = Arrays.asList(
                example(TodayAction.completeOccurrence("occ"), "handleCompleteOccurrence", "occ"),
                example(TodayAction.requestClose("task", "Titel"), "handleRequestClose", "task", "Titel"),
                example(TodayAction.completeRemaining("remaining"), "handleCompleteRemaining", "remaining"),
                example(TodayAction.harvest("harvest"), "handleHarvest", "harvest"),
                example(TodayAction.bringFirst(target), "handleBringFirst", target.item),
                example(TodayAction.defer(target), "handleDefer", target.item),
                example(TodayAction.startFlowCandidate("candidate"), "handleStartFlowCandidate", "candidate", null),
                example(TodayAction.startFlowCandidate("delayed", 71_000L), "handleStartFlowCandidate", "delayed", 71_000L),
                example(TodayAction.completeFlowStep("runtime"), "handleCompleteFlowStep", "runtime", null),
                example(TodayAction.completeFlowStep("runtime-delay", 72_000L), "handleCompleteFlowStep", "runtime-delay", 72_000L),
                example(TodayAction.collectFlow("run"), "handleCollectFlow", "run"),
                example(TodayAction.adjustFlowWait("run", "wait", 73_000L), "handleAdjustFlowWait", "run", "wait", 73_000L),
                example(TodayAction.toggleStep("toggle"), "handleToggleStep", "toggle"),
                example(TodayAction.toggleStep("toggle-delay", 74_000L), "handleToggleStepWithDelay", "toggle-delay", 74_000L),
                example(TodayAction.finishStep("finish"), "handleFinishStep", "finish"),
                example(TodayAction.advanceStep("advance"), "handleAdvanceStep", "advance"),
                example(TodayAction.undoOccurrence("undo"), "handleUndoOccurrence", "undo"),
                example(TodayAction.adjustRepetition("reps", 2), "handleAdjustRepetition", "reps", 2),
                example(TodayAction.adjustTrainingLoad("load", 500), "handleAdjustTrainingLoad", "load", 500),
                example(TodayAction.adjustTrainingRir("rir", -1), "handleAdjustTrainingRir", "rir", -1),
                example(TodayAction.toggleTrainingSafety("safety"), "handleToggleTrainingSafety", "safety"),
                example(TodayAction.editRepetition("edit", 3), "handleEditRepetition", "edit", 3),
                example(TodayAction.submitRepetition("submit"), "handleSubmitRepetition", "submit"),
                example(TodayAction.startDurationTimer("step", "Timer", 90), "handleStartDurationTimer", "step", "Timer", 90),
                example(TodayAction.pauseTimer("pause"), "handlePauseTimer", "pause"),
                example(TodayAction.resumeTimer("resume"), "handleResumeTimer", "resume"),
                example(TodayAction.resetTimer("reset"), "handleResetTimer", "reset"),
                example(TodayAction.observeTimer("observe"), "handleObserveTimer", "observe"));
        EnumSet<TodayCommand.Kind> covered = EnumSet.noneOf(TodayCommand.Kind.class);
        for (Case test : cases) {
            TodayCommand command = command(today, test.action);
            assertFalse("Duplicate command: " + command.kind, covered.contains(command.kind));
            covered.add(command.kind);
            assertDispatch(command, test.handler, test.arguments);
        }
        TodayCommand reorder = command(today, TodayAction.moveStep("a", null));
        assertFalse(reorder.commandId.isEmpty());
        assertDispatch(reorder, "handlePersistReorder", Arrays.asList(reorder.commandId, "a", null));
        covered.add(reorder.kind);
        assertEquals(EnumSet.allOf(TodayCommand.Kind.class), covered);

        assertDispatch(command(today, TodayAction.requestClose("task", null)),
                "handleRequestClose", Arrays.asList("task", ""));
        assertDispatch(command(today, TodayAction.startDurationTimer("step", null, 60)),
                "handleStartDurationTimer", Arrays.asList("step", "", 60));
    }

    private static TodayCommand command(TodayUiModel today, TodayAction action) {
        List<TodayCommand> commands = new ArrayList<>();
        new TodayCoordinator(today, commands::add, state -> { }).emit(action);
        assertEquals(1, commands.size());
        return commands.get(0);
    }

    private static void assertDispatch(TodayCommand command, String handler, List<Object> arguments) {
        List<String> methods = new ArrayList<>();
        List<List<Object>> payloads = new ArrayList<>();
        var handlers = (TodayCommandDispatcher.Handlers) Proxy.newProxyInstance(
                TodayCommandDispatcher.Handlers.class.getClassLoader(),
                new Class<?>[]{TodayCommandDispatcher.Handlers.class}, (proxy, method, args) -> {
                    methods.add(method.getName());
                    payloads.add(Arrays.asList(args));
                    return null;
                });
        new TodayCommandDispatcher(handlers).execute(command);
        assertEquals(command.kind.name(), Collections.singletonList(handler), methods);
        assertEquals(command.kind.name(), Collections.singletonList(arguments), payloads);
    }

    private static Case example(TodayAction action, String handler, Object... arguments) {
        return new Case(action, handler, Arrays.asList(arguments));
    }

    private static final class Case {
        final TodayAction action;
        final String handler;
        final List<Object> arguments;
        Case(TodayAction action, String handler, List<Object> arguments) {
            this.action = action;
            this.handler = handler;
            this.arguments = arguments;
        }
    }
}
