package de.thonktank.autosecretary;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.FrameLayout;
import android.app.Dialog;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w412dp-h892dp-xhdpi")
public final class TaskEditorGoldenRobolectricTest {
    @Test public void allTenWizardRegressionBaselinesRemainStable() throws Exception {
        AssertionError firstFailure = null;
        List<Bitmap> renderings = new ArrayList<>();
        for (TaskEditorGoldenScenario scenario : TaskEditorGoldenScenario.ALL) {
            Bitmap actual = render(scenario);
            renderings.add(actual);
            try { compare(scenario.id, actual); }
            catch (AssertionError failure) { if (firstFailure == null) firstFailure = failure; }
        }
        TaskEditorContactSheet.write(TaskEditorGoldenScenario.ALL, renderings);
        for (Bitmap rendering : renderings) rendering.recycle();
        if (firstFailure != null) throw firstFailure;
    }

    private static Bitmap render(TaskEditorGoldenScenario scenario) {
        Context context = ApplicationProvider.getApplicationContext();
        DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT);
        FrameLayout root = new FrameLayout(context);
        ForestBackdropView forest = new ForestBackdropView(context); forest.setPalette(palette);
        root.addView(forest, new FrameLayout.LayoutParams(-1, -1));
        TaskEditorView editor = new TaskEditorView(context, new NoopEditorListener());
        editor.setPadding(0, dp(context, 28), 0, 0);
        root.addView(editor, new FrameLayout.LayoutParams(-1, -1));
        editor.bind(scenario.state(), palette, TaskEditorGoldenScenario.TODAY);
        root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 824, 1784);
        Bitmap bitmap = Bitmap.createBitmap(824, 1784, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        root.draw(canvas);
        if (scenario.prompt != EditorUiState.Prompt.NONE) {
            Dialog dialog = ShadowDialog.getLatestDialog();
            if (dialog != null && dialog.getWindow() != null) {
                Paint dim = new Paint();
                dim.setColor(0x87060c08);
                canvas.drawRect(0, 0, 824, 1784, dim);
                View decor = dialog.getWindow().getDecorView();
                decor.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1284, View.MeasureSpec.AT_MOST));
                decor.layout(0, 0, 824, decor.getMeasuredHeight());
                canvas.save();
                canvas.translate(0, 500);
                decor.draw(canvas);
                canvas.restore();
            }
        }
        return bitmap;
    }

    private static void compare(String name, Bitmap actual) throws Exception {
        String resource = "/golden/task-editor/wizard/" + name + ".png";
        GoldenAssertions.compare(TaskEditorGoldenRobolectricTest.class, resource,
                new File("src/test/resources/golden/task-editor/wizard", name + ".png"),
                new File("build/reports/goldens/task-editor", name), actual,
                64, .42d, "UPDATE_TASK_EDITOR_GOLDENS");
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
