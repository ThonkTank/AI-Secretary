package de.thonktank.autosecretary;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.presentation.alltasks.AllTasksFilter;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksUiState;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksView;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w412dp-h702dp-xhdpi")
public final class AllTasksRenderRobolectricTest {
    @Test public void standardPlacementCardsRemainVisuallyStable() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT);
        FrameLayout root = new FrameLayout(context);
        ForestBackdropView forest = new ForestBackdropView(context);
        forest.setPalette(palette);
        root.addView(forest, new FrameLayout.LayoutParams(-1, -1));
        AllTasksView view = new AllTasksView(context, new AllTasksView.Listener() { });
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        AllTasksUiState state = AllTasksUiState.from(catalog(), AllTasksFilter.defaults())
                .toggleExpanded(AllTasksUiState.cardKey("morning", TaskSlot.MORNING));
        view.bind(state, palette);
        root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1404, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 824, 1404);
        Bitmap bitmap = Bitmap.createBitmap(824, 1404, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        GoldenAssertions.compare(AllTasksRenderRobolectricTest.class,
                "/golden/all-tasks/standard.png",
                new File("src/test/resources/golden/all-tasks/standard.png"),
                new File("build/reports/goldens/all-tasks/standard"), bitmap,
                0, 0d, "UPDATE_ALL_TASKS_GOLDENS");
        bitmap.recycle();
    }

    private static TaskCatalog catalog() {
        List<TaskCatalog.Item> items = new ArrayList<>();
        items.add(item("morning", "Morgenroutine", Recurrence.DAILY, 1_024,
                Arrays.asList(TaskSlot.MORNING, TaskSlot.EVENING),
                Arrays.asList("Haare waschen", "Anziehen", "Tabletten nehmen")));
        items.add(item("bed", "Bett machen", Recurrence.DAILY, 2_048,
                Collections.singletonList(TaskSlot.MORNING), Collections.emptyList()));
        items.add(item("office", "Abgabe Statistik-Übung", Recurrence.ONCE, 3_072,
                Collections.singletonList(TaskSlot.MIDDAY),
                Collections.singletonList("Aufgabenblatt lesen")));
        items.add(item("wash", "Wäsche waschen", Recurrence.WEEKDAYS, 4_096,
                Collections.singletonList(TaskSlot.EVENING),
                Arrays.asList("Wäsche sortieren", "Maschine anstellen", "Aufhängen")));
        return new TaskCatalog(items);
    }

    private static TaskCatalog.Item item(String id, String title, Recurrence recurrence,
                                         long order, List<TaskSlot> slots, List<String> steps) {
        Task task = Task.restore(TaskId.of(id), title, recurrence, 1,
                recurrence == Recurrence.WEEKDAYS ? 1 | 8 : 0, false, "", false, false,
                LocalDate.of(2026, 8, 23), null, null, order, false, null,
                TaskBoundKind.FOREVER, null, null, null, null, "");
        List<TaskScheduleEntry> schedule = new ArrayList<>();
        for (TaskSlot slot : slots)
            schedule.add(new TaskScheduleEntry(id + '-' + slot.name(), task.id, slot, order));
        List<TaskStepTemplate> templates = new ArrayList<>();
        for (int index = 0; index < steps.size(); index++)
            templates.add(new TaskStepTemplate(id + "-step-" + index, task.id,
                    index, steps.get(index)));
        return new TaskCatalog.Item(task, templates, schedule);
    }
}
