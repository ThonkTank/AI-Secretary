package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.calendar.CalendarResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.time.LocalTime;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class UiComponentRobolectricTest {
    @Test public void activityCreatesTheComponentShell() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            assertEquals(false, activity.isFinishing());
        }
    }

    @Test public void validatorCoversEveryEditorConstraint() {
        TaskEditorValidator validator = new TaskEditorValidator();
        EditorUiState base = EditorUiState.create();
        assertEquals(TaskEditorValidator.Error.TITLE, validator.validate(base));
        EditorUiState weekdays = base.withDraft("Routine", TaskSlot.MORNING,
                Recurrence.WEEKDAYS, 1, 0, Collections.emptyList(), false, "");
        assertEquals(TaskEditorValidator.Error.WEEKDAYS, validator.validate(weekdays));
        EditorUiState ongoing = base.withDraft("Vorhaben", TaskSlot.LATER,
                Recurrence.ONCE, 1, 0, Collections.emptyList(), true, "");
        assertEquals(TaskEditorValidator.Error.CONDITION, validator.validate(ongoing));
        assertEquals(TaskEditorValidator.Error.NONE, validator.validate(
                ongoing.withDraft("Vorhaben", TaskSlot.LATER, Recurrence.ONCE, 1, 0,
                        Collections.emptyList(), true, "Vertrag unterschrieben")));
    }

    @Test public void rendererReusesTheMountedViewTreeForNormalUpdates() {
        Context context = ApplicationProvider.getApplicationContext();
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        DashboardRenderer renderer = new DashboardRenderer(context, scroll, content,
                new NoOpActions(), "1.0");
        DayPalette morning = DayPalette.at(LocalTime.of(8, 0), DayPalette.Mode.AUTO);
        DashboardUiState first = state(morning);

        renderer.render(first, UiThemeMode.AUTO);
        View focus = content.getChildAt(1);
        focus.setFocusableInTouchMode(true);
        focus.requestFocus();

        renderer.render(state(DayPalette.at(LocalTime.of(8, 1), DayPalette.Mode.AUTO)),
                UiThemeMode.AUTO);

        assertSame(focus, content.getChildAt(1));
        assertSame(focus, content.findFocus());
    }

    @Test public void primaryNavigationControlsMeetAccessibilityContracts() {
        Context context = ApplicationProvider.getApplicationContext();
        int target = context.getResources().getDimensionPixelSize(R.dimen.touch_target);
        HeaderView header = new HeaderView(context, () -> { });
        View add = header.getChildAt(1);
        assertTrue(((android.widget.TextView) add).getMinWidth() >= target);
        assertTrue(((android.widget.TextView) add).getMinHeight() >= target);
        assertEquals(context.getString(R.string.content_add_task), add.getContentDescription());

        FooterNavigationView footer = new FooterNavigationView(context, destination -> { });
        for (int index = 0; index < footer.getChildCount(); index++) {
            View item = footer.getChildAt(index);
            android.widget.TextView text = (android.widget.TextView) item;
            assertTrue(text.getMinWidth() >= target);
            assertTrue(text.getMinHeight() >= target);
            assertTrue(text.getText().length() > 0);
        }
    }

    private static DashboardUiState state(DayPalette palette) {
        DashboardUiModel dashboard = DashboardUiModel.compose(
                DashboardFixtures.fullDashboard(), DashboardFixtures.calendarEvents());
        return new DashboardUiState(NavigationDestination.TODAY, dashboard,
                CalendarUiState.from(new CalendarResult.Success(DashboardFixtures.calendarEvents())), palette,
                CalendarPermissionStatus.GRANTED, false, Collections.emptySet(),
                EditorUiState.closed());
    }

    private static final class NoOpActions implements DashboardRenderer.Actions {
        @Override public void onAddTask() { }
        @Override public void onTaskAction(TaskSnapshot task) { }
        @Override public void onTaskMenu(TaskSnapshot task) { }
        @Override public void onComplete(TaskSnapshot task) { }
        @Override public void onDefer(TaskSnapshot task) { }
        @Override public void onToggleStep(TaskStepSnapshot step) { }
        @Override public void onTheme(UiThemeMode mode) { }
        @Override public void onCalendarPermission() { }
        @Override public void onUpdates() { }
    }
}
