package de.thonktank.autosecretary;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private static final String[] DIALOG = {"01-einfache-aufgabe", "02-feste-wochentage",
            "03-taeglich-befristet", "04-intervall", "05-schritte-liste",
            "06-schritt-bearbeiten", "07-schritt-verschieben", "08-schritt-fehler",
            "09-mehrmals-am-tag", "10-schritte-mit-menge", "11-menge-bearbeiten",
            "12-notizen"};
    private static final String[] FOUNDATIONS = {"01-freie-minuten", "02-fehler-am-feld",
            "03-abbrechen-rueckfrage", "04-loeschen-rueckfrage"};

    @Test public void allSeventeenReferenceStatesRenderAndRemainPerceptuallyAligned() throws Exception {
        for (String name : DIALOG) compare("dialog", name, render(name));
        for (String name : FOUNDATIONS) compare("grundzustaende", name, render(name));
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
        root.draw(new Canvas(bitmap)); return bitmap;
    }

    private static EditorUiState fixture(String name) {
        EditorUiState state = EditorUiState.create();
        String title = name.contains("intervall") ? "Pflanzen gießen"
                : name.contains("mehrmals") || name.contains("befristet")
                ? "Nachbarskatze füttern" : name.contains("schritt")
                || name.contains("menge") || name.contains("notizen")
                ? "Kraft üben" : "Steuerbescheid prüfen";
        Recurrence recurrence = name.contains("wochentage") ? Recurrence.WEEKDAYS
                : name.contains("befristet") || name.contains("mehrmals") ? Recurrence.DAILY
                : name.contains("intervall") ? Recurrence.INTERVAL : Recurrence.ONCE;
        int weekdays = recurrence == Recurrence.WEEKDAYS ? 1 | 8 : 0;
        int times = recurrence == Recurrence.ONCE ? 0 : TimeOfDay.MORNING.bit;
        if (name.contains("mehrmals")) times |= TimeOfDay.EVENING.bit;
        TaskBoundKind bound = name.contains("befristet") || name.contains("mehrmals")
                ? TaskBoundKind.FOR_WEEKS : name.contains("intervall")
                ? TaskBoundKind.N_TIMES : TaskBoundKind.FOREVER;
        List<EditorStepState> steps = steps(name);
        String expanded = name.equals("06-schritt-bearbeiten") || name.equals("11-menge-bearbeiten")
                ? steps.get(0).id : null;
        Integer duration = name.equals("01-freie-minuten") || name.equals("02-fehler-am-feld")
                ? name.equals("02-fehler-am-feld") ? 0 : 75 : 15;
        String note = name.equals("12-notizen") ? "Studio Nordstraße, Schrank 14" : "";
        state = state.draft(title, TaskSlot.MORNING, duration, recurrence,
                recurrence == Recurrence.INTERVAL ? 2 : 1, weekdays, times, bound,
                bound == TaskBoundKind.FOR_WEEKS ? TODAY.plusWeeks(2) : null,
                bound == TaskBoundKind.FOR_WEEKS ? 2 : null,
                bound == TaskBoundKind.N_TIMES ? 10 : null,
                name.equals("02-fehler-am-feld") ? TODAY.minusDays(1) : null,
                note, steps, expanded, steps.size() + 1);
        Set<String> errors = new LinkedHashSet<>();
        if (name.equals("08-schritt-fehler")) errors.add(TaskEditorValidator.STEP_PREFIX
                + steps.get(1).id);
        if (name.equals("02-fehler-am-feld")) {
            errors.add(TaskEditorValidator.DURATION); errors.add(TaskEditorValidator.BOUND);
        }
        if (!errors.isEmpty()) state = state.withFeedback(errors,
                EditorUiState.Prompt.NONE, "");
        if (name.equals("03-abbrechen-rueckfrage"))
            state = state.withFeedback(Collections.emptySet(), EditorUiState.Prompt.DISCARD, "");
        if (name.equals("04-loeschen-rueckfrage")) {
            Bundle bundle = state.toBundle(); bundle.putString("task_id", "edit-task");
            state = EditorUiState.fromBundle(bundle).withFeedback(Collections.emptySet(),
                    EditorUiState.Prompt.DELETE, "");
        }
        return state;
    }

    private static List<EditorStepState> steps(String name) {
        if (!(name.contains("schritt") || name.contains("menge") || name.contains("notizen")))
            return Collections.emptyList();
        List<EditorStepState> result = new ArrayList<>();
        if (name.equals("08-schritt-fehler")) {
            result.add(new EditorStepState("s1", "Küche", 0, StepAmount.none(), ""));
            result.add(EditorStepState.blank(2));
            return result;
        }
        String[] labels = name.contains("menge") || name.contains("notizen")
                ? new String[]{"Liegestütze", "Kniebeugen", "Planke"}
                : new String[]{"Staubsaugen", "Bad", "Küche", "Müll rausbringen"};
        for (int i = 0; i < labels.length; i++) {
            StepAmountKind kind = name.contains("menge") || name.contains("notizen")
                    ? i < 2 ? StepAmountKind.SETS_REPS : StepAmountKind.DURATION
                    : StepAmountKind.NONE;
            result.add(new EditorStepState("s" + i, labels[i], i == 1 ? 1 | 8 : 0,
                    kind == StepAmountKind.SETS_REPS
                            ? StepAmount.setsReps(3, 12 + i * 3)
                            : kind == StepAmountKind.DURATION
                            ? StepAmount.duration(45) : StepAmount.none(),
                    name.equals("12-notizen") && i < 2 ? i == 0 ? "23 kg, Sitz 5"
                            : "35 kg, Griff weit" : ""));
        }
        return result;
    }

    private static void compare(String folder, String name, Bitmap actual) throws Exception {
        String resource = "/golden/task-editor/" + folder + "/" + name + ".png";
        GoldenAssertions.compare(TaskEditorGoldenRobolectricTest.class, resource,
                new File("src/test/resources/golden/task-editor/" + folder, name + ".png"),
                new File("build/reports/goldens/task-editor", folder + "-" + name), actual,
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
