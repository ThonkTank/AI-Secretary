package com.autosecretary.features.task.ui.list;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.R;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.ui.list.state.ViewSlot;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.TaskFixtures;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Protects the row rendering visibility matrix of the agenda layout by inflating the real
 * layouts and driving {@link ListRowAdapter} bind paths directly:
 * <ul>
 *   <li>The time rail + state line appear only in Checklist mode; other modes carry the time
 *       range in the metadata line instead.</li>
 *   <li>Goal tasks show the compact stepper instead of the checkbox; the remaining-time bar
 *       appears only in Frist mode.</li>
 *   <li>Category headers render via their own view type without a card; calendar rows are
 *       read-only with a "Kalender" chip.</li>
 *   <li>The title may wrap to two lines (never letter-width squeezing).</li>
 * </ul>
 */
public final class TaskRowRenderingCharacterizationTest extends AutoSecretaryRobolectricTest {

    private final Context themedContext =
            new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme);

    private RecyclerView recyclerView;
    private ListRowAdapter adapter;

    private void createAdapter(List<ViewSlot> slots) {
        recyclerView = new RecyclerView(themedContext);
        recyclerView.setLayoutManager(new LinearLayoutManager(themedContext));
        adapter = new ListRowAdapter(new ArrayList<>(slots), new ListRowAdapter.TaskRowActions(
                slot -> { }, slot -> { }, slot -> { }, slot -> { }, slot -> { }, slot -> { },
                slot -> true));
        recyclerView.setAdapter(adapter);
    }

    private View bindRow(int position) {
        RecyclerView.ViewHolder holder =
                adapter.onCreateViewHolder(recyclerView, adapter.getItemViewType(position));
        adapter.onBindViewHolder(holder, position);
        return holder.itemView;
    }

    private static List<ViewSlot> toViewSlots(List<Task> tasks) {
        return new TaskListItemMapper().map(tasks).stream()
                .map(ViewSlot::new)
                .collect(Collectors.toList());
    }

    private static void assertVisibility(View row, int viewId, int expectedVisibility, String what) {
        View view = row.findViewById(viewId);
        assertEquals(what, expectedVisibility, view.getVisibility());
    }

    @Test
    public void checklistShowsTimeRailAndStateLineWithTwoLineTitleInvariant() {
        LocalDate today = LocalDate.now();
        Task task = TaskFixtures.taskWithSlot("Wohnung gründlich saugen und wischen", today);
        createAdapter(toViewSlots(List.of(task)));

        View row = bindRow(0);
        assertVisibility(row, R.id.TimeRail, View.VISIBLE, "time rail visible in Checklist mode");
        assertVisibility(row, R.id.StateLine, View.VISIBLE, "state line visible in Checklist mode");
        assertVisibility(row, R.id.MetaTimeRange, View.GONE, "no metadata time in Checklist mode");
        assertVisibility(row, R.id.TaskCheckBox, View.VISIBLE, "checkbox visible for plain task");
        assertVisibility(row, R.id.DeadlineProgressBar, View.GONE, "no deadline bar outside Frist mode");
        TextView title = row.findViewById(R.id.TaskTitle);
        assertEquals("title may wrap to two lines", 2, title.getMaxLines());
        assertEquals("10:00", ((TextView) row.findViewById(R.id.TimeStart)).getText().toString());
        assertEquals("10:30", ((TextView) row.findViewById(R.id.TimeEnd)).getText().toString());
    }

    @Test
    public void goalTaskShowsCompactStepperInsteadOfCheckboxInvariant() {
        LocalDate today = LocalDate.now();
        Task goalTask = TaskFixtures.taskWithSlot("Buch lesen", today);
        goalTask.core.progress.target = 10;
        goalTask.core.progress.current = 3;
        goalTask.core.progress.unit = "Seiten";
        createAdapter(toViewSlots(List.of(goalTask)));

        View row = bindRow(0);
        assertVisibility(row, R.id.ProgressContainer, View.VISIBLE, "stepper visible for goal task");
        assertVisibility(row, R.id.TaskCheckBox, View.GONE, "checkbox hidden for goal task");
        String progressText = ((TextView) row.findViewById(R.id.ProgressText)).getText().toString();
        assertTrue("stepper shows current/target (was " + progressText + ")",
                progressText.contains("3/10"));
    }

    @Test
    public void urgencyModeCarriesTimeInMetadataLineInsteadOfRailInvariant() {
        LocalDate today = LocalDate.now();
        Task task = TaskFixtures.taskWithSlot("Steuern", today);
        task.core.deadline = today.plusDays(3);
        createAdapter(toViewSlots(List.of(task)));
        adapter.setDisplayMode(ListConfig.URGENCY);

        View row = bindRow(0);
        assertVisibility(row, R.id.TimeRail, View.GONE, "no time rail outside Checklist mode");
        assertVisibility(row, R.id.StateLine, View.GONE, "no state line outside Checklist mode");
        assertVisibility(row, R.id.MetaTimeRange, View.VISIBLE, "time range moves to the metadata line");
        assertVisibility(row, R.id.DeadlineCountdown, View.VISIBLE, "deadline label visible");
        assertVisibility(row, R.id.DeadlineProgressBar, View.GONE, "no deadline bar in Priorität mode");
    }

    @Test
    public void deadlineModeShowsRemainingTimeBarInvariant() {
        LocalDate today = LocalDate.now();
        Task task = new Task();
        task.core.title = "Steuererklärung";
        task.core.created = today.minusDays(5);
        task.core.deadline = today.plusDays(5);
        createAdapter(toViewSlots(List.of(task)));
        adapter.setDisplayMode(ListConfig.DEADLINE);

        View row = bindRow(0);
        assertVisibility(row, R.id.DeadlineProgressBar, View.VISIBLE, "remaining-time bar in Frist mode");
        assertVisibility(row, R.id.TimeRail, View.GONE, "no time rail in Frist mode");
    }

    @Test
    public void categoryHeaderRendersWithoutCardInvariant() {
        ViewSlot header = new ViewSlot(
                TaskListItem.categoryHeader("cat-1", "Haushalt", "🧹", "#FF5C7A4D"));
        createAdapter(List.of(header));
        adapter.setDisplayMode(ListConfig.MANAGE);

        View row = bindRow(0);
        assertNull("category header has no task card", row.findViewById(R.id.TaskCard));
        assertEquals("Haushalt", ((TextView) row.findViewById(R.id.HeaderTitle)).getText().toString());
        assertVisibility(row, R.id.HeaderIcon, View.VISIBLE, "category icon visible");
    }

    @Test
    public void calendarEventRowIsReadOnlyWithChipInvariant() {
        LocalDate today = LocalDate.now();
        ViewSlot event = new ViewSlot(TaskListItem.calendarEvent(
                "event-1", "Zahnarzt", today, LocalTime.of(11, 0), LocalTime.of(12, 0)));
        createAdapter(List.of(event));

        View row = bindRow(0);
        assertVisibility(row, R.id.CalendarChip, View.VISIBLE, "calendar chip visible");
        assertVisibility(row, R.id.TaskCheckBox, View.GONE, "no checkbox on calendar rows");
        assertVisibility(row, R.id.TaskStateButton, View.GONE, "no state button on calendar rows");
        assertVisibility(row, R.id.TimeRail, View.VISIBLE, "calendar rows keep the time rail in Checklist mode");
    }
}
