package de.thonktank.autosecretary;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TimeOfDay;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w412dp-h892dp-xhdpi")
public final class TaskEditorGoldenRobolectricTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 17);
    private static final String[] STATES = {"01-titel-zeitraum", "02-titel-fehler",
            "03-titel-einmalig", "04-rhythmus-wochentage", "05-rhythmus-intervall",
            "06-schritte", "07-schritt-detail", "08-uebersicht",
            "09-abbrechen-rueckfrage", "10-loeschen-rueckfrage"};

    @Test public void allTenApprovedWizardStatesRenderAndRemainPerceptuallyAligned() throws Exception {
        AssertionError firstFailure = null;
        for (String name : STATES) {
            try { compare(name, render(name)); }
            catch (AssertionError failure) { if (firstFailure == null) firstFailure = failure; }
        }
        if (firstFailure != null) throw firstFailure;
    }

    private static Bitmap render(String name) {
        Context context = ApplicationProvider.getApplicationContext();
        DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT);
        FrameLayout root = new FrameLayout(context);
        ForestBackdropView forest = new ForestBackdropView(context); forest.setPalette(palette);
        root.addView(forest, new FrameLayout.LayoutParams(-1, -1));
        TaskEditorView editor = new TaskEditorView(context, new NoopEditorListener());
        editor.setPadding(0, dp(context, 28), 0, 0);
        root.addView(editor, new FrameLayout.LayoutParams(-1, -1));
        editor.bind(fixture(name), palette, TODAY);
        root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 824, 1784);
        Bitmap bitmap = Bitmap.createBitmap(824, 1784, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        root.draw(canvas);
        if (name.contains("rueckfrage")) {
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

    private static EditorUiState fixture(String name) {
        EditorUiState state = EditorUiState.create();
        String title = name.equals("02-titel-fehler") ? "" : name.contains("schritt")
                ? "Kraft üben" : name.contains("uebersicht") || name.contains("rueckfrage")
                ? "Morgenroutine" : name.contains("intervall") ? "Pflanzen gießen"
                : name.contains("einmalig") ? "Abgabe Statistik-Übung" : "Morgenroutine";
        Recurrence recurrence = name.contains("einmalig") ? Recurrence.ONCE
                : name.contains("intervall") ? Recurrence.INTERVAL
                : name.contains("wochentage") || name.contains("uebersicht")
                || name.contains("rueckfrage") ? Recurrence.WEEKDAYS : Recurrence.DAILY;
        int weekdays = recurrence == Recurrence.WEEKDAYS ? 31 : 0;
        int times = recurrence == Recurrence.ONCE ? 0 : TimeOfDay.MORNING.bit;
        TaskBoundKind bound = recurrence == Recurrence.ONCE ? TaskBoundKind.FOREVER
                : name.equals("01-titel-zeitraum") || name.contains("uebersicht")
                || name.contains("rueckfrage") ? TaskBoundKind.FOR_WEEKS
                : TaskBoundKind.FOREVER;
        List<EditorStepState> steps = steps(name);
        String expanded = name.equals("07-schritt-detail")
                ? steps.get(0).id : null;
        Integer duration = name.contains("uebersicht") || name.contains("rueckfrage") ? 20 : 30;
        String note = name.contains("uebersicht") || name.contains("rueckfrage")
                ? "Tabletten liegen im Bad, nicht in der Küche." : "";
        state = state.draft(title, TaskSlot.MORNING, duration, recurrence,
                recurrence == Recurrence.INTERVAL ? 3 : 1, weekdays, times, bound,
                bound == TaskBoundKind.FOR_WEEKS ? TODAY.plusWeeks(6) : null,
                bound == TaskBoundKind.FOR_WEEKS ? 6 : null, null,
                recurrence == Recurrence.ONCE ? TODAY.plusDays(12) : null,
                note, steps, expanded, steps.size() + 1);
        EditorUiState.Page page = name.startsWith("04-") || name.startsWith("05-")
                ? EditorUiState.Page.SCHEDULE : name.startsWith("06-") || name.startsWith("07-")
                ? EditorUiState.Page.STEPS : name.startsWith("08-") || name.startsWith("10-")
                ? EditorUiState.Page.SUMMARY : EditorUiState.Page.TITLE;
        state = state.withPage(page, false);
        if (name.equals("02-titel-fehler")) state = state.withFeedback(
                Collections.singleton(ValidationIssue.task(ValidationIssue.Field.TITLE)),
                EditorUiState.Prompt.NONE, "");
        if (name.equals("09-abbrechen-rueckfrage"))
            state = state.withFeedback(Collections.emptySet(), EditorUiState.Prompt.DISCARD, "");
        if (name.equals("08-uebersicht") || name.equals("10-loeschen-rueckfrage")) {
            Bundle bundle = state.toBundle(); bundle.putString("task_id", "edit-task");
            state = EditorUiState.fromBundle(bundle);
            if (name.equals("10-loeschen-rueckfrage")) state = state.withFeedback(
                    Collections.emptySet(), EditorUiState.Prompt.DELETE, "");
        }
        return state;
    }

    private static List<EditorStepState> steps(String name) {
        if (!(name.contains("schritt") || name.contains("uebersicht")
                || name.contains("rueckfrage")))
            return Collections.emptyList();
        List<EditorStepState> result = new ArrayList<>();
        String[] labels = name.contains("uebersicht") || name.contains("rueckfrage")
                ? new String[]{"Haare waschen", "Tabletten", "Dehnen"}
                : new String[]{"Liegestütze", "Kniebeugen", "Planke"};
        for (int i = 0; i < labels.length; i++) {
            StepAmountKind kind = name.equals("07-schritt-detail")
                    ? i < 2 ? StepAmountKind.SETS_REPS : StepAmountKind.DURATION
                    : StepAmountKind.NONE;
            result.add(new EditorStepState("s" + i, labels[i], i == 1 ? 1 | 8 : 0,
                    kind == StepAmountKind.SETS_REPS
                            ? StepAmount.setsReps(3, 12 + i * 3)
                            : kind == StepAmountKind.DURATION
                            ? StepAmount.duration(45) : StepAmount.none(),
                    name.equals("07-schritt-detail") && i == 0 ? "23 kg, Sitz 5" : ""));
        }
        return result;
    }

    private static void compare(String name, Bitmap actual) throws Exception {
        String resource = "/golden/task-editor/wizard/" + name + ".png";
        GoldenAssertions.compare(TaskEditorGoldenRobolectricTest.class, resource,
                new File("src/test/resources/golden/task-editor/wizard", name + ".png"),
                new File("build/reports/goldens/task-editor", name), actual,
                64, .42d, "UPDATE_TASK_EDITOR_GOLDENS");
        actual.recycle();
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
