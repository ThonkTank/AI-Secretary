package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.today.CalendarEventSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RewardBreakdown;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.RewardTextFormatter;
import de.thonktank.autosecretary.presentation.today.CompletedTaskUiModel;
import de.thonktank.autosecretary.presentation.today.FocusStepStatus;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.StepExecutionUiAction;
import de.thonktank.autosecretary.presentation.today.TaskActionTarget;
import de.thonktank.autosecretary.presentation.today.TimelineItemUiModel;
import de.thonktank.autosecretary.presentation.today.TimelineStepUiModel;
import de.thonktank.autosecretary.presentation.today.TimelineTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.presentation.today.XpVesselUiModel;
import de.thonktank.autosecretary.widget.WidgetDashboardUiModel;
import de.thonktank.autosecretary.widget.WidgetStepUiModel;
import de.thonktank.autosecretary.widget.WidgetTaskUiModel;

/** Deterministic debug-only states for the reference preview gallery and layout inspectors. */
public final class DebugPreviewFixtures {
    private static final RewardTextFormatter REWARDS = new RewardTextFormatter(Locale.GERMANY);

    private DebugPreviewFixtures() { }

    public static TodayUiModel busyDay() {
        PreviewTask focus = PreviewTask.named("preview-morning", "Morgenroutine")
                .recurrence(Recurrence.DAILY).steps(Arrays.asList(
                        step("preview-step-1", "Duschen", true),
                        step("preview-step-2", "Anziehen", false),
                        step("preview-step-3", "Frühstück", false)))
                .combo(6).order(1_001_000L);
        PreviewTask after = PreviewTask.named("preview-letter", "Brief beantworten")
                .slot(TaskSlot.MIDDAY).softTime("um die Mittagszeit").order(2_001_000L);
        return today(120, focus, after);
    }

    public static WidgetDashboardUiModel widgetReference() {
        WidgetTaskUiModel gym = WidgetTaskUiModel.of("preview-gym",
                "preview-gym-occurrence", "Gym Routine", false, false, "Rest erledigen",
                Arrays.asList(
                        WidgetStepUiModel.of("preview-gym-1", "Bankdrücken",
                                "3 Sätze · 8 Wiederholungen · 60 kg", true),
                        WidgetStepUiModel.of("preview-gym-2", "Rudern",
                                "3 Sätze · 10 Wiederholungen · 45 kg", false),
                        WidgetStepUiModel.of("preview-gym-3", "Plank",
                                "60 Sekunden · ruhig atmen", false)));
        return WidgetDashboardUiModel.of(gym, "Brief beantworten");
    }

    public static TodayUiModel emptyDay() { return TodayUiModel.empty(); }

    public static List<CalendarEventSnapshot> calendar() {
        return Arrays.asList(new CalendarEventSnapshot("ganztägig", "Urlaub", 0),
                new CalendarEventSnapshot("10:15", "Arzttermin", 10 * 60 + 15));
    }

    public static TodayUiModel reference(String state) {
        if ("empty-vessel".equals(state)) return today(70, vesselTask(0, false));
        if ("partial-vessel".equals(state)) return today(70, vesselTask(1, false));
        if ("harvest-ready".equals(state)) return today(70, vesselTask(3, true));
        if ("three-digit".equals(state)) return today(70, threeDigitTask());
        PreviewTask morning = morning(false, "step".equals(state));
        PreviewTask after = PreviewTask.named("preview-after", "Abgabe Statistik-Übung")
                .softTime("voraussichtlich ab 10:15").order(2_000L);
        PreviewTask laundry = PreviewTask.named("preview-laundry", "Wäsche aufhängen")
                .slot(TaskSlot.LATER).order(3_000L);
        PreviewTask hiddenOne = PreviewTask.named("preview-hidden-1", "Einkauf planen")
                .slot(TaskSlot.LATER).order(4_000L);
        PreviewTask hiddenTwo = PreviewTask.named("preview-hidden-2", "Pflanzen gießen")
                .slot(TaskSlot.LATER).order(5_000L);
        if ("empty".equals(state)) return TodayUiModel.empty();
        if ("later".equals(state)) return today(120, after, morning, laundry, hiddenOne, hiddenTwo);
        if ("complete".equals(state) || "harvested".equals(state))
            return today(120, morning(true, true), after, laundry, hiddenOne, hiddenTwo);
        if ("evening".equals(state)) {
            PreviewTask ongoing = PreviewTask.named("preview-ongoing", "Praktikum")
                    .slot(TaskSlot.LATER).softTime("fortlaufend, bis es angenommen ist")
                    .terminal(true).ongoing(true).combo(6)
                    .reward(RewardBreakdown.fromStage(10, 0)).order(900L);
            return today(150, ongoing, morning(true, true), laundry);
        }
        return today(120, morning, after, laundry, hiddenOne, hiddenTwo);
    }

    public static List<CalendarEventSnapshot> referenceCalendar(String state) {
        if ("empty".equals(state) || "evening".equals(state)) return Collections.emptyList();
        return Collections.singletonList(new CalendarEventSnapshot("11:00", "Zahnarzt", 11 * 60));
    }

    private static PreviewTask morning(boolean done, boolean secondStepDone) {
        return PreviewTask.named("preview-morning", "Morgenroutine")
                .softTime("etwa eine halbe Stunde")
                .recurrence(Recurrence.DAILY).done(done).combo(6).order(1_000L)
                .steps(Arrays.asList(step("preview-step-1", "Duschen", true),
                        step("preview-step-2", "Haare waschen", secondStepDone || done),
                        step("preview-step-3", "Anziehen", done),
                        step("preview-step-4", "Tabletten nehmen", done)));
    }

    private static PreviewTask vesselTask(int completed, boolean ready) {
        return PreviewTask.named("preview-vessel", "Morgenroutine")
                .recurrence(Recurrence.DAILY).combo(5).harvestReady(ready).order(1_000L)
                .steps(Arrays.asList(step("vessel-1", "Duschen", completed >= 1,
                                RewardBreakdown.fromStage(10, 0), 2),
                        step("vessel-2", "Haare waschen", completed >= 2,
                                RewardBreakdown.fromStage(10, 1), 3),
                        step("vessel-3", "Anziehen", completed >= 3,
                                RewardBreakdown.fromStage(10, 2), 5)));
    }

    private static PreviewTask threeDigitTask() {
        return PreviewTask.named("preview-three-digit", "Steuerunterlagen abgeben")
                .overdue(true).combo(12).reward(RewardBreakdown.fromStage(25, 8))
                .order(1_000L);
    }

    private static FocusStepUiModel step(String id, String title, boolean done) {
        return step(id, title, done, RewardBreakdown.fromStage(10, 0));
    }

    private static FocusStepUiModel step(String id, String title, boolean done,
                                         RewardBreakdown reward) {
        return step(id, title, done, reward, reward.comboStage);
    }

    private static FocusStepUiModel step(String id, String title, boolean done,
                                         RewardBreakdown reward, int grainLevel) {
        return FocusStepUiModel.executableWithGrainLevel(id, title, "", "",
                done ? FocusStepStatus.COMPLETED : FocusStepStatus.AVAILABLE,
                done ? StepExecutionUiAction.none()
                        : StepExecutionUiAction.advancePlannedRepetitions(id),
                null, reward, grainLevel, done ? reward.resultXp : 0);
    }

    private static TodayUiModel today(int xp, PreviewTask... examples) {
        PreviewTask focusSource = null;
        int open = 0;
        for (PreviewTask example : examples) if (!example.done) {
            if (focusSource == null) focusSource = example;
            open++;
        }
        FocusTaskUiModel focus = focusSource == null ? null : focusSource.focus(open > 1);
        List<TimelineItemUiModel> timeline = new ArrayList<>();
        List<CompletedTaskUiModel> completed = new ArrayList<>();
        for (PreviewTask example : examples) {
            if (example.done) completed.add(example.completed());
            else if (example != focusSource) timeline.add(TimelineItemUiModel.task(example.timeline()));
        }
        return new TodayUiModel(new XpProgress(xp), focus, timeline, completed);
    }

    private static final class PreviewTask {
        final String id;
        final String title;
        TaskSlot slot = TaskSlot.MORNING;
        String softTime = "";
        Recurrence recurrence = Recurrence.ONCE;
        List<FocusStepUiModel> steps = Collections.emptyList();
        boolean done;
        boolean overdue;
        boolean ongoing;
        boolean terminal;
        boolean harvestReady;
        int comboStage;
        RewardBreakdown explicitReward;
        long order;

        private PreviewTask(String id, String title) { this.id = id; this.title = title; }
        static PreviewTask named(String id, String title) { return new PreviewTask(id, title); }
        PreviewTask slot(TaskSlot value) { slot = value; return this; }
        PreviewTask softTime(String value) { softTime = value; return this; }
        PreviewTask recurrence(Recurrence value) { recurrence = value; return this; }
        PreviewTask steps(List<FocusStepUiModel> value) { steps = value; return this; }
        PreviewTask done(boolean value) { done = value; return this; }
        PreviewTask overdue(boolean value) { overdue = value; return this; }
        PreviewTask ongoing(boolean value) { ongoing = value; return this; }
        PreviewTask terminal(boolean value) { terminal = value; return this; }
        PreviewTask harvestReady(boolean value) { harvestReady = value; return this; }
        PreviewTask combo(int value) { comboStage = value; return this; }
        PreviewTask reward(RewardBreakdown value) { explicitReward = value; return this; }
        PreviewTask order(long value) { order = value; return this; }

        TaskActionTarget target() {
            return TaskActionTarget.of(id, id + "-occurrence", title, slot,
                    recurrence != Recurrence.ONCE, terminal);
        }

        RewardBreakdown reward() {
            if (explicitReward != null) return explicitReward;
            int base = 0;
            for (FocusStepUiModel step : steps) base += step.earnedXp;
            if (steps.isEmpty()) base = 10;
            return RewardBreakdown.fromStage(base, comboStage);
        }

        FocusTaskUiModel focus(boolean allowDefer) {
            List<FocusStepUiModel> explicit = new ArrayList<>();
            boolean activeAssigned = false;
            int remaining = 0;
            for (FocusStepUiModel step : steps) {
                if (step.isDone()) explicit.add(step);
                else {
                    FocusStepStatus status = activeAssigned ? FocusStepStatus.AVAILABLE
                            : FocusStepStatus.ACTIVE;
                    explicit.add(FocusStepUiModel.executableWithGrainLevel(step.id, step.title,
                            step.amountLabel, step.note, status,
                            activeAssigned ? StepExecutionUiAction.advancePlannedRepetitions(step.id)
                                    : StepExecutionUiAction.toggle(step.id),
                            step.repetitionProgress, step.reward, step.grainLevel,
                            step.earnedXp));
                    activeAssigned = true;
                    remaining++;
                }
            }
            RewardBreakdown reward = reward();
            return FocusTaskUiModel.builder(target()).nextAction("erledigen")
                    .steps(explicit, remaining).ongoing(ongoing).overdue(overdue)
                    .allowDefer(allowDefer).harvestReady(harvestReady)
                    .grainLevel(comboStage)
                    .reward(reward, XpVesselUiModel.of(reward, explicit.size() - remaining,
                            explicit.size(), harvestReady, REWARDS)).build();
        }

        TimelineTaskUiModel timeline() {
            List<TimelineStepUiModel> values = new ArrayList<>();
            for (FocusStepUiModel step : steps)
                values.add(TimelineStepUiModel.completion(step.isDone()));
            return TimelineTaskUiModel.of(target(), id, id + "-occurrence", title, slot,
                    softTime, values, terminal, overdue, order, reward());
        }

        CompletedTaskUiModel completed() {
            return CompletedTaskUiModel.of(id + "-occurrence", title, reward().resultXp, true);
        }
    }
}
