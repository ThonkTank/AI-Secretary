package de.thonktank.autosecretary;

import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

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
import java.util.function.Consumer;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w412dp-h702dp-xhdpi")
public final class AllTasksRenderRobolectricTest {
    private static final LocalTime DAY = LocalTime.of(9, 40);

    @Test public void standardPlacementCardsRemainVisuallyStable() throws Exception {
        assertGolden("standard", standardState(), 412, 1f, DAY,
                DayPalette.Mode.LIGHT, view -> { });
    }

    @Test public void openFiltersAndDropdownRemainVisuallyStable() throws Exception {
        assertGolden("filters-dropdown", AllTasksUiState.from(catalog(true),
                        AllTasksFilter.defaults()), 412, 1f, DAY, DayPalette.Mode.LIGHT,
                view -> {
                    TextView slots = find(view, "Tageszeit ⌄");
                    assertNotNull(slots);
                    slots.performClick();
                });
    }

    @Test public void titleSearchMatchRemainsVisuallyStable() throws Exception {
        assertGolden("search-title", AllTasksUiState.from(catalog(true),
                        AllTasksFilter.defaults()).withQuery("Statistik"),
                412, 1f, DAY, DayPalette.Mode.LIGHT, view -> { });
    }

    @Test public void stepSearchMatchRemainsVisuallyStable() throws Exception {
        assertGolden("search-step", AllTasksUiState.from(catalog(true),
                        AllTasksFilter.defaults()).withQuery("Aufgabenblatt"),
                412, 1f, DAY, DayPalette.Mode.LIGHT, view -> { });
    }

    @Test public void archivedCardRemainsVisuallyStable() throws Exception {
        AllTasksUiState state = AllTasksUiState.from(catalog(true), AllTasksFilter.defaults())
                .withStatus(AllTasksUiState.Status.ARCHIVED)
                .toggleExpanded(AllTasksUiState.cardKey("archive", TaskSlot.LATER));
        assertGolden("archived", state, 412, 1f, DAY,
                DayPalette.Mode.LIGHT, view -> { });
    }

    @Test public void sortModeWithVisibleDropTargetsRemainsVisuallyStable() throws Exception {
        AllTasksUiState state = AllTasksUiState.from(catalog(true), AllTasksFilter.defaults())
                .withMode(AllTasksUiState.Mode.SORT);
        assertGolden("sort-drag-targets", state, 412, 1f, DAY,
                DayPalette.Mode.LIGHT, AllTasksRenderRobolectricTest::showDragTargets);
    }

    @Test public void emptySearchRemainsVisuallyStable() throws Exception {
        AllTasksUiState state = AllTasksUiState.from(catalog(true), AllTasksFilter.defaults())
                .withQuery("nicht vorhanden");
        assertGolden("empty-search", state, 412, 1f, DAY,
                DayPalette.Mode.LIGHT, view -> { });
    }

    @Test public void emptyFilterRemainsVisuallyStable() throws Exception {
        AllTasksUiState state = AllTasksUiState.from(catalog(true), AllTasksFilter.defaults())
                .withSlots(Collections.singleton(TaskSlot.LATER));
        assertGolden("empty-filter", state, 412, 1f, DAY,
                DayPalette.Mode.LIGHT, view -> { });
    }

    @Test public void emptyStatusRemainsVisuallyStable() throws Exception {
        AllTasksUiState state = AllTasksUiState.from(catalog(false), AllTasksFilter.defaults())
                .withStatus(AllTasksUiState.Status.ARCHIVED);
        assertGolden("empty-status", state, 412, 1f, DAY,
                DayPalette.Mode.LIGHT, view -> { });
    }

    @Test public void narrowLayoutWithRaisedTextRemainsVisuallyStable() throws Exception {
        assertGolden("width-320-font-1_3", standardState(), 320, 1.3f, DAY,
                DayPalette.Mode.LIGHT, view -> { });
    }

    @Test public void largeTextLayoutRemainsVisuallyStable() throws Exception {
        assertGolden("width-412-font-2_0", standardState(), 412, 2f, DAY,
                DayPalette.Mode.LIGHT, view -> { });
    }

    @Test public void tabletLayoutRemainsVisuallyStable() throws Exception {
        assertGolden("width-600", standardState(), 600, 1f, DAY,
                DayPalette.Mode.LIGHT, view -> { });
    }

    @Test public void darkNightPaletteRemainsVisuallyStable() throws Exception {
        assertGolden("night", standardState(), 412, 1f, LocalTime.of(23, 50),
                DayPalette.Mode.DARK, view -> { });
    }

    private static AllTasksUiState standardState() {
        return AllTasksUiState.from(catalog(true), AllTasksFilter.defaults())
                .toggleExpanded(AllTasksUiState.cardKey("morning", TaskSlot.MORNING));
    }

    private static void assertGolden(String name, AllTasksUiState state, int widthDp,
                                     float fontScale, LocalTime time, DayPalette.Mode mode,
                                     Consumer<AllTasksView> interaction) throws Exception {
        Context context = configuredContext(widthDp, fontScale);
        DayPalette palette = DayPalette.at(time, mode);
        FrameLayout root = new FrameLayout(context);
        ForestBackdropView forest = new ForestBackdropView(context);
        forest.setPalette(palette);
        root.addView(forest, new FrameLayout.LayoutParams(-1, -1));
        AllTasksView view = new AllTasksView(context, new AllTasksView.Listener() { });
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        view.bind(state, palette);
        shadowOf(Looper.getMainLooper()).idle();
        interaction.accept(view);
        shadowOf(Looper.getMainLooper()).idle();

        float density = context.getResources().getDisplayMetrics().density;
        int width = Math.round(widthDp * density);
        int height = Math.round(702 * density);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        shadowOf(Looper.getMainLooper()).idle();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        GoldenAssertions.compare(AllTasksRenderRobolectricTest.class,
                "/golden/all-tasks/" + name + ".png",
                new File("src/test/resources/golden/all-tasks", name + ".png"),
                new File("build/reports/goldens/all-tasks", name), bitmap,
                0, 0d, "UPDATE_ALL_TASKS_GOLDENS");
        bitmap.recycle();
    }

    private static Context configuredContext(int widthDp, float fontScale) {
        Context base = ApplicationProvider.getApplicationContext();
        if (widthDp == 412 && fontScale == 1f) return base;
        Configuration configuration = new Configuration(
                base.getResources().getConfiguration());
        configuration.screenWidthDp = widthDp;
        configuration.screenHeightDp = 702;
        configuration.fontScale = fontScale;
        return base.createConfigurationContext(configuration);
    }

    private static TextView find(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText()))
            return (TextView) root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int index = 0; index < group.getChildCount(); index++) {
            TextView result = find(group.getChildAt(index), text);
            if (result != null) return result;
        }
        return null;
    }

    private static void showDragTargets(AllTasksView view) {
        try {
            java.lang.reflect.Method method = AllTasksView.class.getDeclaredMethod(
                    "setDragActiveForTest", boolean.class);
            method.setAccessible(true);
            method.invoke(view, true);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not activate the characterized drag state", error);
        }
    }

    private static TaskCatalog catalog(boolean includeArchived) {
        List<TaskCatalog.Item> items = new ArrayList<>();
        items.add(item("morning", "Morgenroutine", Recurrence.DAILY, 1_024,
                Arrays.asList(TaskSlot.MORNING, TaskSlot.EVENING),
                Arrays.asList("Haare waschen", "Anziehen", "Tabletten nehmen"), false));
        items.add(item("bed", "Bett machen", Recurrence.DAILY, 2_048,
                Collections.singletonList(TaskSlot.MORNING), Collections.emptyList(), false));
        items.add(item("office", "Abgabe Statistik-Übung", Recurrence.ONCE, 3_072,
                Collections.singletonList(TaskSlot.MIDDAY),
                Collections.singletonList("Aufgabenblatt lesen"), false));
        items.add(item("wash", "Wäsche waschen", Recurrence.WEEKDAYS, 4_096,
                Collections.singletonList(TaskSlot.EVENING),
                Arrays.asList("Wäsche sortieren", "Maschine anstellen", "Aufhängen"), false));
        if (includeArchived)
            items.add(item("archive", "Winterprojekt", Recurrence.ONCE, 5_120,
                    Collections.singletonList(TaskSlot.LATER),
                    Collections.singletonList("Kisten sortieren"), true));
        return new TaskCatalog(items);
    }

    private static TaskCatalog.Item item(String id, String title, Recurrence recurrence,
                                         long order, List<TaskSlot> slots, List<String> steps,
                                         boolean archived) {
        Task task = Task.restore(TaskId.of(id), title, recurrence, 1,
                recurrence == Recurrence.WEEKDAYS ? 1 | 8 : 0, false, "", false, archived,
                archived ? null : LocalDate.of(2026, 8, 23),
                archived ? LocalDate.of(2026, 8, 20) : null,
                archived ? LocalDate.of(2026, 8, 20) : null,
                order, archived, null, TaskBoundKind.FOREVER,
                null, null, null, null, "");
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
