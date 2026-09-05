package de.thonktank.autosecretary;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.presentation.today.FocusStepRowUiModel;
import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.RepetitionProgressUiModel;
import de.thonktank.autosecretary.timer.TimerManager;
import de.thonktank.autosecretary.ui.today.FocusStepRowView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Reviewed visual matrix for point wrapping and the two supported mobile widths. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "xhdpi")
public final class SetDotsGoldenRobolectricTest {
    @Test public void allRequiredSetCountsKeepTheResponsivePointRhythm() throws Exception {
        int[] sets = {3, 8, 20, 21, 40};
        int[] widths = {412, 320, 412, 320, 412};
        float[] scales = {1f, 1f, 2f, 2f, 1.3f};
        DayPalette.Mode[] modes = {DayPalette.Mode.LIGHT, DayPalette.Mode.DARK,
                DayPalette.Mode.DARK, DayPalette.Mode.LIGHT, DayPalette.Mode.LIGHT};
        AssertionError first = null;
        for (int index = 0; index < sets.length; index++) {
            try {
                verify(sets[index], widths[index], scales[index], modes[index]);
            } catch (AssertionError failure) {
                if (first == null) first = failure;
            }
        }
        if (first != null) throw first;
    }

    private static void verify(int sets, int widthDp, float fontScale,
                               DayPalette.Mode mode) throws Exception {
        Context base = ApplicationProvider.getApplicationContext();
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.screenWidthDp = widthDp;
        configuration.fontScale = fontScale;
        Context context = base.createConfigurationContext(configuration);
        DayPalette palette = DayPalette.at(LocalTime.of(9, 40), mode);
        List<Integer> completed = new ArrayList<>();
        for (int index = 0; index < Math.min(13, sets - 1); index++) completed.add(12);
        FocusStepUiModel step = FocusTaskFixtures.step("sets-" + sets, "Beinpresse")
                .amount(sets + " × 12")
                .repetition(RepetitionProgressUiModel.sets(sets, 12, completed))
                .build();
        FocusStepRowView row = new FocusStepRowView(context);
        row.setBackgroundColor(palette.leaf1);
        row.bind(FocusStepRowUiModel.expanded(step), palette, RepetitionInputState.idle(),
                TimerManager.Snapshot.empty(), event -> { });
        int width = Math.round((widthDp - 24) * context.getResources()
                .getDisplayMetrics().density);
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, width, row.getMeasuredHeight());
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        Bitmap actual = Bitmap.createBitmap(width, row.getMeasuredHeight(),
                Bitmap.Config.ARGB_8888);
        row.draw(new Canvas(actual));
        String name = "set-dots-" + sets + '-' + widthDp + "dp-"
                + Math.round(fontScale * 10) + '-' + mode.name().toLowerCase();
        GoldenAssertions.compare(SetDotsGoldenRobolectricTest.class,
                "/golden/focus-task/" + name + ".png",
                new File("src/test/resources/golden/focus-task", name + ".png"),
                new File("build/reports/goldens/focus-task", name), actual,
                0, 0d, "UPDATE_FOCUS_TASK_GOLDENS");
        actual.recycle();
    }
}
