package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalTime;
import java.util.Collections;

import de.thonktank.autosecretary.calendar.CalendarResult;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class WidgetPresenterTest {
    private final Context context = ApplicationProvider.getApplicationContext();
    private final WidgetPresenter presenter = new WidgetPresenter(context);
    private final DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);

    @Test public void everySizeGetsItsDocumentedProjection() {
        WidgetPresenter.CycleData data = new WidgetPresenter.CycleData(
                DashboardFixtures.widgetDashboard(),
                new CalendarResult.Success(DashboardFixtures.calendarEvents()), palette);

        WidgetUiModel small = presenter.present(data, WidgetSizeClassifier.Size.SMALL);
        WidgetUiModel wide = presenter.present(data, WidgetSizeClassifier.Size.WIDE);
        WidgetUiModel tall = presenter.present(data, WidgetSizeClassifier.Size.TALL);
        WidgetUiModel large = presenter.present(data, WidgetSizeClassifier.Size.LARGE);

        assertTrue(small.steps.isEmpty());
        assertEquals(java.util.Arrays.asList(true, false), small.progress);
        assertNull(small.calendar);
        assertEquals(2, wide.steps.size());
        assertEquals("Duschen", wide.steps.get(0).title);
        assertEquals("3 Sätze · 8 Wiederholungen", wide.steps.get(0).subtitle);
        assertNull(wide.calendar);
        assertEquals("Urlaub", tall.calendar.title);
        assertEquals("Rechnung bezahlen", large.afterTitle);
        assertEquals(WidgetUiModel.PrimaryAction.NONE, large.primaryAction);
        assertTrue(large.showAdd);
    }

    @Test public void emptyProjectionOnlyOffersSafeEditorNavigation() {
        WidgetPresenter.CycleData data = new WidgetPresenter.CycleData(
                DashboardFixtures.emptyDashboard(),
                new CalendarResult.Success(Collections.emptyList()), palette);

        WidgetUiModel small = presenter.present(data, WidgetSizeClassifier.Size.SMALL);
        WidgetUiModel large = presenter.present(data, WidgetSizeClassifier.Size.LARGE);

        assertTrue(small.empty);
        assertEquals(WidgetUiModel.PrimaryAction.OPEN_EDITOR, small.primaryAction);
        assertFalse(small.showAdd);
        assertTrue(large.showAdd);
        assertEquals(WidgetUiModel.PrimaryAction.NONE, large.primaryAction);
    }

    @Test public void terminalConditionIsNeverExecutedDirectlyByTheWidget() {
        TodayUiModel state = DashboardFixtures.today(0,
                Collections.singletonList(DashboardFixtures.ongoingTask()));
        WidgetUiModel model = presenter.present(new WidgetPresenter.CycleData(state,
                        new CalendarResult.PermissionMissing(), palette),
                WidgetSizeClassifier.Size.TALL);

        assertEquals(WidgetUiModel.PrimaryAction.CONFIRM_CLOSE, model.primaryAction);
        assertEquals("ongoing", model.primaryActionId);
    }
}
