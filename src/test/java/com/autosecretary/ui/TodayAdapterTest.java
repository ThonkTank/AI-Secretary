package com.autosecretary.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.widget.TextView;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.R;
import com.autosecretary.application.MoveWorkItemUseCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class TodayAdapterTest {
    @Test
    public void collapsedAndExpandedViewsShareOneThreeRowLimit() {
        List<TodayRow> rows = new ArrayList<>();
        LocalDateTime start = LocalDateTime.of(2026, 8, 14, 10, 0);
        for (int index = 0; index < 5; index++) {
            rows.add(new TodayRow.Calendar(new CalendarRow(start.plusHours(index),
                    start.plusHours(index + 1), "Termin " + index)));
        }
        Dashboard dashboard = new Dashboard(rows, List.of());

        assertEquals(3, MainActivity.visibleTodayRows(dashboard, false).size());
        assertEquals(5, MainActivity.visibleTodayRows(dashboard, true).size());
    }

    @Test
    public void calendarRowUsesSharedPositionMarkerAndHasNoTaskAction() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout parent = new FrameLayout(context);
        TodayAdapter adapter = new TodayAdapter(new NoOpListener());
        LocalDateTime start = LocalDateTime.of(2026, 8, 14, 10, 0);
        adapter.submit(List.of(new TodayRow.Calendar(
                new CalendarRow(start, start.plusHours(1), "Arzt"))));

        RecyclerView.ViewHolder holder = adapter.onCreateViewHolder(parent,
                adapter.getItemViewType(0));
        adapter.onBindViewHolder(holder, 0);

        assertEquals("jetzt · im Kalender, fest",
                ((TextView) holder.itemView.findViewById(R.id.CalendarLabel)).getText().toString());
        assertNull(holder.itemView.findViewById(R.id.FocusDone));
    }

    private static final class NoOpListener implements TodayAdapter.Listener {
        @Override public void onComplete(String id) { }
        @Override public void onStepChanged(String id, String stepId, boolean completed) { }
        @Override public void onMove(String id, MoveWorkItemUseCase.Direction direction) { }
        @Override public void onOmit(String id) { }
    }
}
