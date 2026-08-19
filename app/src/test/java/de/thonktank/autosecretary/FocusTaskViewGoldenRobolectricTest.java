package de.thonktank.autosecretary;

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

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;

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
        view.bind(gymTask(), false, false, palette, new NoOpActions());
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root);

        int width = 824;
        int height = 1100;
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
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
        TaskStepSnapshot press = new TaskStepSnapshot("press", "Beinpresse", false,
                StepAmountKind.SETS_REPS, 3, 12, null, "23 kg, Sitz 5",
                Arrays.asList(12, 11));
        TaskStepSnapshot pushups = new TaskStepSnapshot("pushups", "Liegestütze", false,
                StepAmountKind.REPS, null, 20, null, "", Collections.emptyList());
        TaskStepSnapshot plank = new TaskStepSnapshot("plank", "Planke", false,
                StepAmountKind.DURATION, null, null, 120, "Bauch fest",
                Collections.emptyList());
        return new TaskSnapshot("gym", "gym-today", "Gym", TaskSlot.MORNING,
                "heute am Morgen", "Beinpresse", Recurrence.DAILY,
                Arrays.asList(press, pushups, plank), 3, false, false, false,
                false, 0, 1L);
    }

    private static final class NoOpActions implements FocusTaskView.Actions {
        @Override public void onComplete(TaskSnapshot task) { }
        @Override public void onDefer(TaskSnapshot task) { }
        @Override public void onToggleStep(TaskStepSnapshot step) { }
    }
}
