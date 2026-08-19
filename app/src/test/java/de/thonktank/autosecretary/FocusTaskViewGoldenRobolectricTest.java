package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.RepetitionProgressUiModel;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w412dp-h892dp-xhdpi")
public final class FocusTaskViewGoldenRobolectricTest {
    @Test public void gymRoutineKeepsAmountsAndNotesReadable() throws Exception {
        render("gym-routine", gymTask(), FocusStepLimit.AUTO, false, 824, 1100, 2);
    }

    @Test public void allFourAmountKindsShareTheModularStepLayout() throws Exception {
        render("all-amount-kinds", allAmountKindsTask(), FocusStepLimit.FIVE, true,
                824, 1450, 4);
    }

    @Test public void activeStepWithoutAmountStaysACompactDirectAction() throws Exception {
        render("active-without-amount", activeWithoutAmountTask(), FocusStepLimit.AUTO, true,
                824, 900, 1);
    }

    private static void render(String name, TaskSnapshot task, FocusStepLimit limit,
                               boolean allowDefer,
                               int width, int height, int following) throws Exception {
        DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(palette.background);
        FocusTaskView view = new FocusTaskView(activity);
        NoOpActions actions = new NoOpActions();
        view.bind(task, false, allowDefer, palette, limit,
                RepetitionInputState.idle(), actions, actions);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root);

        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        assertEquals("focus=" + view.getMeasuredHeight() + " root=" + root.getMeasuredHeight(),
                following, view.visibleFollowingStepsForTest());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        WoodGrainView.awaitGeometryForTest();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Bitmap actual = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(actual));
        GoldenAssertions.compare(FocusTaskViewGoldenRobolectricTest.class,
                "/golden/focus-task/" + name + ".png",
                new File("src/test/resources/golden/focus-task", name + ".png"),
                new File("build/reports/goldens/focus-task", name), actual,
                0, 0d, "UPDATE_FOCUS_TASK_GOLDENS");
        actual.recycle();
    }

    private static TaskSnapshot gymTask() {
        FocusStepUiModel press = new FocusStepUiModel("press", "Beinpresse",
                "3 × 12", "23 kg, Sitz 5", false,
                RepetitionProgressUiModel.sets(3, 12, Arrays.asList(12, 11)),
                0, 10, 0);
        FocusStepUiModel pushups = new FocusStepUiModel("pushups", "Liegestütze",
                "20 Wdh.", "", false,
                RepetitionProgressUiModel.single(20, java.util.Collections.emptyList()),
                0, 10, 0);
        FocusStepUiModel plank = new FocusStepUiModel("plank", "Planke",
                "2 Min.", "Bauch fest", false,
                null, 0, 10, 0);
        return new TaskSnapshot("gym", "gym-today", "Gym", TaskSlot.MORNING,
                "heute am Morgen", "Beinpresse", Recurrence.DAILY,
                Arrays.asList(press, pushups, plank), 3, false, false, false,
                false, 0, 1L);
    }

    private static TaskSnapshot allAmountKindsTask() {
        FocusStepUiModel warmup = step("warmup", "Aufwärmen", "", "", true, null);
        FocusStepUiModel squats = step("squats", "Kniebeugen", "3 × 12",
                "Hantel 10 kg, langsam runter", false,
                RepetitionProgressUiModel.sets(3, 12, Collections.singletonList(12)));
        FocusStepUiModel pushups = step("pushups", "Liegestütze", "12 Wdh.",
                "auf Fäusten", false,
                RepetitionProgressUiModel.single(12, Collections.emptyList()));
        FocusStepUiModel plank = step("plank", "Plank", "45 Sek.", "", false, null);
        FocusStepUiModel stretch = step("stretch", "Dehnen", "",
                "Waden und Hüfte, ohne Eile", false, null);
        FocusStepUiModel shower = step("shower", "Duschen", "", "", false, null);
        return new TaskSnapshot("morning", "morning-today", "Morgenroutine",
                TaskSlot.MORNING, "", "Kniebeugen", Recurrence.DAILY,
                Arrays.asList(warmup, squats, pushups, plank, stretch, shower),
                5, false, false, false, false, 1, 1L);
    }

    private static TaskSnapshot activeWithoutAmountTask() {
        FocusStepUiModel shower = step("shower", "Duschen", "", "", false, null);
        return new TaskSnapshot("morning-late", "morning-late-today", "Morgenroutine",
                TaskSlot.MORNING, "", "Dehnen", Recurrence.DAILY,
                Arrays.asList(
                        step("warmup", "Aufwärmen", "", "", true, null),
                        step("squats", "Kniebeugen", "3 × 12", "", true,
                                RepetitionProgressUiModel.sets(3, 12,
                                        Arrays.asList(12, 12, 12))),
                        step("pushups", "Liegestütze", "12 Wdh.", "", true,
                                RepetitionProgressUiModel.single(12,
                                        Collections.singletonList(12))),
                        step("plank", "Plank", "45 Sek.", "", true, null),
                        step("stretch", "Dehnen", "",
                                "Waden und Hüfte, ohne Eile", false, null),
                        shower), 2, false, false, false, false, 4, 1L);
    }

    private static FocusStepUiModel step(String id, String title, String amount, String note,
                                        boolean done,
                                        RepetitionProgressUiModel progress) {
        return new FocusStepUiModel(id, title, amount, note, done, progress,
                0, 10, done ? 10 : 0);
    }

    private static final class NoOpActions extends FocusTestActions { }
}
