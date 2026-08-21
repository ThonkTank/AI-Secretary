package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import de.thonktank.autosecretary.domain.model.Dashboard;
import de.thonktank.autosecretary.domain.model.DashboardTask;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.widget.WidgetDashboardMapper;
import de.thonktank.autosecretary.widget.WidgetDashboardUiModel;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class WidgetDashboardMapperTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Test public void mapsDomainStraightIntoWidgetOwnedContent() {
        Context context = ApplicationProvider.getApplicationContext();
        Task gym = task("gym", "Gym", 1);
        Occurrence occurrence = occurrence(gym, "gym-today");
        OccurrenceStep step = new OccurrenceStep("squats", occurrence.id, 0,
                "Kniebeugen", false, StepAmount.setsReps(3, 8), "60 kg",
                Collections.singletonList(8));
        Task invoice = task("invoice", "Rechnung bezahlen", 2);
        Dashboard dashboard = new Dashboard(0, Arrays.asList(
                new DashboardTask(gym, occurrence, Collections.singletonList(step), false),
                new DashboardTask(invoice, occurrence(invoice, "invoice-today"),
                        Collections.emptyList(), false)));

        WidgetDashboardUiModel result = new WidgetDashboardMapper(
                new AndroidUiTextProvider(context)).map(dashboard, TODAY);

        assertNotNull(result.focus);
        assertEquals("Gym", result.focus.title);
        assertEquals("Kniebeugen", result.focus.steps.get(0).title);
        assertEquals("3 × 8 Wdh. · 60 kg",
                result.focus.steps.get(0).subtitle);
        assertFalse(result.focus.steps.get(0).done);
        assertEquals("Rechnung bezahlen", result.afterTitle);
    }

    private static Task task(String id, String title, long order) {
        return Task.create(TaskId.of(id), title, TaskSlot.MORNING, Recurrence.DAILY,
                1, 0, false, "", TODAY, order);
    }

    private static Occurrence occurrence(Task task, String id) {
        return new Occurrence(id, task.id, TODAY, TaskSlot.MORNING, OccurrenceState.OPEN,
                0, null);
    }
}
