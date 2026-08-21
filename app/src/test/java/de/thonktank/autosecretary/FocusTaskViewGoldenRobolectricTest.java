package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.RepetitionProgressUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;

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

    private static void render(String name, FocusTaskUiModel task, FocusStepLimit limit,
                               boolean allowDefer,
                               int width, int height, int following) throws Exception {
        DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(palette.background);
        FocusTaskView view = new FocusTaskView(activity);
        view.bind(task, false, palette, limit,
                RepetitionInputState.idle(), event -> { });
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root);

        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        assertEquals("focus=" + view.getMeasuredHeight() + " root=" + root.getMeasuredHeight(),
                following, ViewTestQueries.visibleFollowingStepRows(view));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        WoodGrainRenderPipeline.awaitIdleForTest();
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

    private static FocusTaskUiModel gymTask() {
        FocusStepUiModel press = FocusTaskFixtures.step("press", "Beinpresse")
                .amount("3 × 12").note("23 kg, Sitz 5")
                .repetition(RepetitionProgressUiModel.sets(3, 12,
                        Arrays.asList(12, 11))).build();
        FocusStepUiModel pushups = FocusTaskFixtures.step("pushups", "Liegestütze")
                .amount("20 Wdh.").repetition(RepetitionProgressUiModel.single(
                        20, java.util.Collections.emptyList())).build();
        FocusStepUiModel plank = FocusTaskFixtures.step("plank", "Planke")
                .amount("2 Min.").note("Bauch fest").build();
        return FocusTaskFixtures.task("gym", "Gym").occurrence("gym-today")
                .slot(TaskSlot.MORNING).recurrence(Recurrence.DAILY)
                .steps(Arrays.asList(press, pushups, plank)).build();
    }

    private static FocusTaskUiModel allAmountKindsTask() {
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
        return FocusTaskFixtures.task("morning", "Morgenroutine")
                .occurrence("morning-today").slot(TaskSlot.MORNING)
                .recurrence(Recurrence.DAILY).allowDefer(true).combo(1)
                .steps(Arrays.asList(warmup, squats, pushups, plank, stretch, shower)).build();
    }

    private static FocusTaskUiModel activeWithoutAmountTask() {
        FocusStepUiModel shower = step("shower", "Duschen", "", "", false, null);
        return FocusTaskFixtures.task("morning-late", "Morgenroutine")
                .occurrence("morning-late-today").slot(TaskSlot.MORNING)
                .recurrence(Recurrence.DAILY).allowDefer(true).combo(4).steps(Arrays.asList(
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
                        shower)).build();
    }

    private static FocusStepUiModel step(String id, String title, String amount, String note,
                                        boolean done,
                                        RepetitionProgressUiModel progress) {
        return FocusTaskFixtures.step(id, title).amount(amount).note(note).done(done)
                .repetition(progress).combo(2).earnedXp(done ? 20 : 0).build();
    }

}
