package de.thonktank.autosecretary;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w412dp-h892dp-xhdpi")
public final class TaskEditorAdaptiveGoldenRobolectricTest {
    @Test public void nightAndCompactRegressionBaselinesRemainStable() throws Exception {
        AssertionError firstFailure = null;
        List<Bitmap> renderings = new ArrayList<>();
        for (TaskEditorAdaptiveGoldenScenario scenario : TaskEditorAdaptiveGoldenScenario.ALL) {
            Bitmap actual = render(scenario);
            renderings.add(actual);
            try { compare(scenario.id, actual); }
            catch (AssertionError failure) { if (firstFailure == null) firstFailure = failure; }
        }
        try { TaskEditorAdaptiveContactSheet.write(
                TaskEditorAdaptiveGoldenScenario.ALL, renderings); }
        catch (AssertionError failure) { if (firstFailure == null) firstFailure = failure; }
        for (Bitmap rendering : renderings) rendering.recycle();
        if (firstFailure != null) throw firstFailure;
    }

    private static Bitmap render(TaskEditorAdaptiveGoldenScenario scenario) {
        Context context = configuredContext(scenario);
        DayPalette palette = DayPalette.at(scenario.time, scenario.paletteMode);
        FrameLayout root = new FrameLayout(context);
        ForestBackdropView forest = new ForestBackdropView(context);
        forest.setPalette(palette);
        root.addView(forest, new FrameLayout.LayoutParams(-1, -1));
        TaskEditorView editor = new TaskEditorView(context, new NoopEditorListener());
        editor.setPadding(0, dp(context, 28), 0, 0);
        root.addView(editor, new FrameLayout.LayoutParams(-1, -1));
        editor.bind(scenario.state(), palette, TaskEditorGoldenScenario.TODAY);
        int width = dp(context, scenario.widthDp);
        int height = dp(context, scenario.heightDp);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static Context configuredContext(TaskEditorAdaptiveGoldenScenario scenario) {
        Context base = ApplicationProvider.getApplicationContext();
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.screenWidthDp = scenario.widthDp;
        configuration.screenHeightDp = scenario.heightDp;
        configuration.fontScale = scenario.fontScale;
        return base.createConfigurationContext(configuration);
    }

    private static void compare(String name, Bitmap actual) throws Exception {
        String resource = "/golden/task-editor/adaptive/" + name + ".png";
        GoldenAssertions.compare(TaskEditorAdaptiveGoldenRobolectricTest.class, resource,
                new File("src/test/resources/golden/task-editor/adaptive", name + ".png"),
                new File("build/reports/goldens/task-editor-adaptive", name), actual,
                64, .42d, "UPDATE_TASK_EDITOR_ADAPTIVE_GOLDENS");
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class NoopEditorListener implements TaskEditorView.Listener {
        @Override public void onDraftChanged(EditorUiState draft) { }
        @Override public void onSave(EditorUiState draft) { }
        @Override public void onDelete(String taskId) { }
        @Override public void onDismiss() { }
    }
}
