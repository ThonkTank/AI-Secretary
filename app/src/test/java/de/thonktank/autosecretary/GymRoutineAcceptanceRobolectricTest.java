package de.thonktank.autosecretary;

import de.thonktank.autosecretary.ui.today.*;

import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayActionSink;

import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.TrainingPrescription;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.CompleteRemainingSteps;
import de.thonktank.autosecretary.domain.usecase.RecordRepetitionResult;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.LoadDashboard;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.ToggleStep;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;
import de.thonktank.autosecretary.testing.InMemoryExecutionRepository;

/** User-value acceptance test from an authored routine to the rendered focus card. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class GymRoutineAcceptanceRobolectricTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    @Test public void authoredGymDetailsSurviveMaterializationAndReachTheFocusCard() {
        Context context = ApplicationProvider.getApplicationContext();
        InMemoryExecutionRepository repository = new InMemoryExecutionRepository();
        FixedClock clock = new FixedClock();
        SequenceIds ids = new SequenceIds();
        List<TaskStepDefinition> steps = Arrays.asList(
                step(0, "Beinpresse", StepAmountKind.SETS_REPS,
                        3, 12, null, "23kg, Sitz 5"),
                step(1, "Liegestütze", StepAmountKind.REPS,
                        null, 20, null, ""),
                step(2, "Planke", StepAmountKind.DURATION,
                        null, null, 120, "Bauch fest"),
                step(3, "Cooldown", StepAmountKind.NONE,
                        null, null, null, "ruhig atmen"));
        TaskDefinition gym = new TaskDefinition("Gym", 45, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", steps);

        new CreateTask(repository.catalog, repository.steps, repository.today, repository.transactions, clock, ids).execute(gym);
        new MaterializeDueOccurrences(repository.catalog, repository.steps, repository.today, repository.flows, repository.transactions, clock, ids).execute();
        TodayUiModel dashboard = new DashboardUiMapper(new AndroidUiTextProvider(context)).map(
                new LoadDashboard(repository.catalog, repository.steps, repository.today, repository.flows).execute(TODAY), TODAY);
        FocusTaskUiModel focus = dashboard.focus;
        assertTrue("The materialized gym routine must become today's focus", focus != null);
        assertEquals(4, focus.steps.size());
        assertEquals("3 × 12", focus.steps.get(0).amountLabel);
        assertEquals("23kg, Sitz 5", focus.steps.get(0).note);
        assertEquals(12, focus.steps.get(0).repetitionProgress.plannedRepetitions);
        assertTrue(focus.steps.get(0).repetitionProgress.repetitions.isEmpty());
        assertEquals("20 Wdh.", focus.steps.get(1).amountLabel);
        assertEquals("2 Min.", focus.steps.get(2).amountLabel);
        assertEquals("Bauch fest", focus.steps.get(2).note);
        assertEquals("ruhig atmen", focus.steps.get(3).note);

        FocusTaskView view = new FocusTaskView(context);
        RecordRepetitionResult record = new RecordRepetitionResult(repository.catalog, repository.steps, repository.today, repository.transactions, clock);
        CompleteRemainingSteps completeRest = new CompleteRemainingSteps(repository.catalog, repository.steps, repository.today, repository.transactions, clock);
        TodayActionSink actions = action -> {
            if (action.kind == TodayAction.Kind.SUBMIT_REPETITION) {
                record.execute(action.id, 12);
            } else if (action.kind == TodayAction.Kind.COMPLETE_REMAINING) {
                completeRest.execute(action.id);
            }
        };
        view.bind(FocusCardTestModels.of(focus,
                DayPalette.at(clock.time(), DayPalette.Mode.LIGHT)), false, actions);

        List<String> texts = visibleTexts(view);
        assertTrue(texts.contains("23kg, Sitz 5"));
        assertTrue(texts.contains("12"));
        assertTrue(contentDescriptions(view).stream()
                .anyMatch(value -> value.contains("Satz 1 mit 12 Wiederholungen")));

        for (int set = 0; set < 3; set++) {
            FocusTaskUiModel current = dashboard(repository, clock, context).focus;
            view.bind(FocusCardTestModels.of(current,
                    DayPalette.at(clock.time(), DayPalette.Mode.LIGHT)), false, actions);
            assertTrue(firstDew(view).performClick());
        }

        FocusTaskUiModel advanced = dashboard(repository, clock, context).focus;
        assertTrue(advanced.steps.get(0).isDone());
        assertEquals("Liegestütze", advanced.nextAction);
        view.bind(FocusCardTestModels.of(advanced,
                DayPalette.at(clock.time(), DayPalette.Mode.LIGHT)), false, actions);
        texts = visibleTexts(view);
        assertTrue(!texts.contains("Beinpresse"));
        assertTrue(texts.contains("Liegestütze"));
        assertTrue(firstDew(view).getContentDescription().toString()
                .contains("20 Wiederholungen sichern"));

        TextView rest = firstText(view, "Rest erledigen");
        assertTrue(rest.performClick());
        FocusTaskUiModel completed = dashboard(repository, clock, context).focus;
        assertEquals(0, completed.remainingSteps);
        assertTrue(completed.steps.stream().allMatch(FocusStepUiModel::isDone));
        view.bind(FocusCardTestModels.of(completed,
                DayPalette.at(clock.time(), DayPalette.Mode.LIGHT)), false, actions);
        assertTrue(visibleTexts(view).contains("4 fertig"));
        assertTrue(!visibleTexts(view).contains("Rest erledigen"));
    }

    @Test public void romanChairIsCleanAfterEightDistinctMachineStepsComplete() {
        Context context = ApplicationProvider.getApplicationContext();
        InMemoryExecutionRepository repository = new InMemoryExecutionRepository();
        FixedClock clock = new FixedClock();
        SequenceIds ids = new SequenceIds();
        List<TaskStepDefinition> definitions = new ArrayList<>();
        for (int index = 0; index < 8; index++)
            definitions.add(de.thonktank.autosecretary.testing.StepTestFixtures.definition(
                    "machine-" + index, index, "Gerät " + index, 0, 0,
                    StepAmount.none(), (20 + index) + " kg, Sitz " + index));
        StepPrescription bodyweight = new StepPrescription(StepAmount.setsReps(3, 12),
                RestTimerPolicy.inherit(), new TrainingPrescription(
                ResistanceLoad.bodyweight(), 2));
        definitions.add(new TaskStepDefinition("roman-chair", 8, "Römische Liege", 0, 0,
                bodyweight, null, "", de.thonktank.autosecretary.domain.model.StepActivationKind.SCHEDULED));
        TaskDefinition gym = new TaskDefinition("Gym", null, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", definitions);
        new CreateTask(repository.catalog, repository.steps, repository.today,
                repository.transactions, clock, ids).execute(gym);
        new MaterializeDueOccurrences(repository.catalog, repository.steps, repository.today,
                repository.flows, repository.transactions, clock, ids).execute();
        List<de.thonktank.autosecretary.domain.model.OccurrenceStep> materialized =
                repository.occurrenceSteps(repository.openOccurrences().get(0).id);
        for (int index = 0; index < 8; index++) {
            assertEquals("machine-" + index, materialized.get(index).sourceTemplateId);
            assertEquals((20 + index) + " kg, Sitz " + index, materialized.get(index).note);
            new ToggleStep(repository.catalog, repository.steps, repository.today,
                    repository.transactions, clock).execute(materialized.get(index).id);
        }
        assertEquals("roman-chair", materialized.get(8).sourceTemplateId);
        assertEquals("", materialized.get(8).note);
        assertEquals(ResistanceLoad.bodyweight(),
                materialized.get(8).prescription.plannedLoad());

        TodayUiModel dashboard = dashboard(repository, clock, context);
        FocusStepUiModel roman = dashboard.focus.steps.stream()
                .filter(value -> value.id.equals(materialized.get(8).id))
                .findFirst().orElseThrow();
        assertEquals("", roman.note);
        assertEquals("3 × 12", roman.amountLabel);
        FocusTaskView view = new FocusTaskView(context);
        view.bind(FocusCardTestModels.of(dashboard.focus,
                DayPalette.at(clock.time(), DayPalette.Mode.LIGHT)), false, action -> { });
        List<String> visible = visibleTexts(view);
        assertTrue(visible.contains("Römische Liege"));
        for (int index = 0; index < 8; index++)
            assertFalse(visible.contains((20 + index) + " kg, Sitz " + index));
    }

    private static TodayUiModel dashboard(InMemoryExecutionRepository repository, Clock clock,
                                           Context context) {
        return new DashboardUiMapper(new AndroidUiTextProvider(context)).map(
                new LoadDashboard(repository.catalog, repository.steps, repository.today, repository.flows).execute(clock.today()), clock.today());
    }

    private static TaskStepDefinition step(int position, String title, StepAmountKind kind,
                                           Integer sets, Integer repetitions, Integer duration,
                                           String note) {
        return de.thonktank.autosecretary.testing.StepTestFixtures.definition(null, position, title, 0,
                StepAmount.fromStorage(kind, sets, repetitions, duration), note);
    }

    private static List<String> visibleTexts(View root) {
        List<String> values = new ArrayList<>();
        visitVisible(root, view -> {
            if (view instanceof TextView) values.add(((TextView) view).getText().toString());
        });
        return values;
    }

    private static List<String> contentDescriptions(View root) {
        List<String> values = new ArrayList<>();
        visitVisible(root, view -> {
            CharSequence description = view.getContentDescription();
            if (description != null) values.add(description.toString());
        });
        return values;
    }

    private static DewDotView firstDew(View root) {
        final DewDotView[] result = {null};
        visitVisible(root, view -> {
            if (result[0] == null && view instanceof DewDotView) result[0] = (DewDotView) view;
        });
        return result[0];
    }

    private static TextView firstText(View root, String text) {
        final TextView[] result = {null};
        visitVisible(root, view -> {
            if (result[0] == null && view instanceof TextView
                    && text.contentEquals(((TextView) view).getText()))
                result[0] = (TextView) view;
        });
        return result[0];
    }

    private static void visitVisible(View root, java.util.function.Consumer<View> visitor) {
        if (root.getVisibility() != View.VISIBLE) return;
        visitor.accept(root);
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        for (int index = 0; index < group.getChildCount(); index++)
            visitVisible(group.getChildAt(index), visitor);
    }

    private static final class SequenceIds implements IdGenerator {
        private int value;
        @Override public String nextId() { return "gym-" + ++value; }
    }

    private static final class FixedClock implements Clock {
        @Override public LocalDate today() { return TODAY; }
        @Override public LocalTime time() { return LocalTime.of(9, 40); }
    }
}
