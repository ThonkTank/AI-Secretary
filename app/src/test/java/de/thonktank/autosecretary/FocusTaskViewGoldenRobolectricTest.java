package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.TaskStepUiModel;
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

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w412dp-h892dp-xhdpi")
public final class FocusTaskViewGoldenRobolectricTest {
    @Test public void gymRoutineKeepsAmountsAndNotesReadable() throws Exception {
        DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(palette.background);
        FocusTaskView view = new FocusTaskView(activity);
        NoOpActions actions = new NoOpActions();
        view.bind(gymTask(), false, false, palette, actions, actions);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root);

        int width = 824;
        int height = 1100;
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        assertEquals("focus=" + view.getMeasuredHeight() + " root=" + root.getMeasuredHeight(),
                2, view.visibleFollowingStepsForTest());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        WoodGrainView.awaitGeometryForTest();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Bitmap actual = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(actual));
        GoldenAssertions.compare(FocusTaskViewGoldenRobolectricTest.class,
                "/golden/focus-task/gym-routine.png",
                new File("src/test/resources/golden/focus-task/gym-routine.png"),
                new File("build/reports/goldens/focus-task", "gym-routine"), actual,
                0, 0d, "UPDATE_FOCUS_TASK_GOLDENS");
        actual.recycle();
    }

    private static TaskSnapshot gymTask() {
        TaskStepUiModel press = new TaskStepUiModel("press", "Beinpresse",
                "3 × 12 Wdh. · 23 kg, Sitz 5", "3 × 12", "23 kg, Sitz 5", false,
                RepetitionProgressUiModel.sets(3, 12, Arrays.asList(12, 11)),
                0, 10, 0);
        TaskStepUiModel pushups = new TaskStepUiModel("pushups", "Liegestütze",
                "20 Wdh.", "20 Wdh.", "", false,
                RepetitionProgressUiModel.single(20, java.util.Collections.emptyList()),
                0, 10, 0);
        TaskStepUiModel plank = new TaskStepUiModel("plank", "Planke",
                "2 Min. · Bauch fest", "2 Min.", "Bauch fest", false,
                null, 0, 10, 0);
        return new TaskSnapshot("gym", "gym-today", "Gym", TaskSlot.MORNING,
                "heute am Morgen", "Beinpresse", Recurrence.DAILY,
                Arrays.asList(press, pushups, plank), 3, false, false, false,
                false, 0, 1L);
    }

    private static final class NoOpActions extends FocusTestActions { }
}
