package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.TaskStepUiModel;

import static org.junit.Assert.assertEquals;
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
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.LoadDashboard;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;
import de.thonktank.autosecretary.testing.InMemoryTaskRepository;

/** User-value acceptance test from an authored routine to the rendered focus card. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class GymRoutineAcceptanceRobolectricTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    @Test public void authoredGymDetailsSurviveMaterializationAndReachTheFocusCard() {
        Context context = ApplicationProvider.getApplicationContext();
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        FixedClock clock = new FixedClock();
        SequenceIds ids = new SequenceIds();
        List<TaskStepDefinition> steps = Arrays.asList(
                step(0, "Beinpresse", StepAmountKind.SETS_REPS,
                        3, 12, null, "23 kg, Sitz 5"),
                step(1, "Liegestütze", StepAmountKind.REPS,
                        null, 20, null, ""),
                step(2, "Planke", StepAmountKind.DURATION,
                        null, null, 120, "Bauch fest"),
                step(3, "Cooldown", StepAmountKind.NONE,
                        null, null, null, "ruhig atmen"));
        TaskDefinition gym = new TaskDefinition("Gym", 45, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", steps);

        new CreateTask(repository, clock, ids, new TaskOrdering()).execute(gym);
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        TodayUiModel dashboard = new DashboardUiMapper(new AndroidUiTextProvider(context)).map(
                new LoadDashboard(repository).execute(TODAY), TODAY);
        TaskSnapshot focus = dashboard.firstOpen();
        assertTrue("The materialized gym routine must become today's focus", focus != null);
        assertEquals(4, focus.steps.size());

        FocusTaskView view = new FocusTaskView(context);
        view.bind(focus, false, false,
                DayPalette.at(clock.time(), DayPalette.Mode.LIGHT), new NoOpActions());

        List<String> texts = visibleTexts(view);
        assertTrue(texts.contains("3 × 12 Wdh. · 23 kg, Sitz 5"));
        assertTrue(texts.contains("20 Wdh."));
        assertTrue(texts.contains("2 Min. · Bauch fest"));
        assertTrue(texts.contains("ruhig atmen"));
        assertTrue(contentDescriptions(view).stream()
                .anyMatch(value -> value.contains("Beinpresse, 3 × 12 Wdh. · 23 kg, Sitz 5")));
    }

    private static TaskStepDefinition step(int position, String title, StepAmountKind kind,
                                           Integer sets, Integer repetitions, Integer duration,
                                           String note) {
        return new TaskStepDefinition(null, position, title, 0, kind, sets, repetitions,
                duration, note);
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

    private static final class NoOpActions implements FocusTaskView.Actions {
        @Override public void onComplete(TaskSnapshot task) { }
        @Override public void onDefer(TaskSnapshot task) { }
        @Override public void onToggleStep(TaskStepUiModel step) { }
    }
}
