package com.autosecretary.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.ai.BulkChangeProposal;
import com.autosecretary.ai.OnDeviceBulkEditor;
import com.autosecretary.core.Obligation;
import com.autosecretary.core.PlanItem;
import com.autosecretary.core.PlanMove;
import com.autosecretary.core.PlanStep;
import com.autosecretary.core.RoutineStep;
import com.autosecretary.core.TimePreference;
import com.google.android.material.snackbar.Snackbar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One screen: focus anchor, complete list, direct editors and confirmed local-AI bulk changes. */
public final class MainActivity extends AppCompatActivity {
    private SecretaryRepository repository;
    private OnDeviceBulkEditor bulkEditor;
    private FocusAdapter focusAdapter;
    private ObligationAdapter obligationAdapter;
    private Dashboard dashboard = new Dashboard(Collections.emptyList(), Collections.emptyList());
    private CelebrationView celebration;
    private TextView emptyFocus;
    private TextView modelStatus;

    private final ActivityResultLauncher<String[]> modelPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                modelStatus.setText(R.string.model_importing);
                bulkEditor.importModel(
                        uri,
                        () -> runOnUiThread(() -> {
                            modelStatus.setText(R.string.model_ready);
                            Toast.makeText(this, R.string.model_imported, Toast.LENGTH_SHORT).show();
                        }),
                        error -> runOnUiThread(() -> {
                            modelStatus.setText(R.string.model_missing);
                            showError(error);
                        }));
            });

    private final ActivityResultLauncher<String> calendarPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> reload());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AutoSecretaryApplication app = AutoSecretaryApplication.from(this);
        repository = app.repository();
        bulkEditor = app.bulkEditor();

        celebration = findViewById(R.id.Celebration);
        emptyFocus = findViewById(R.id.EmptyFocus);
        modelStatus = findViewById(R.id.ModelStatus);
        modelStatus.setText(bulkEditor.hasModel() ? R.string.model_ready : R.string.model_missing);

        focusAdapter = new FocusAdapter(new FocusAdapter.Listener() {
            @Override public void onComplete(PlanItem item) { complete(item.obligation()); }
            @Override public void onStepChanged(PlanItem item, PlanStep step, boolean completed) {
                setStepCompleted(item.obligation(), step, completed);
            }
            @Override public void onMove(PlanItem item, PlanMove move) {
                repository.move(item.obligation().id, move, MainActivity.this::reload);
            }
        });
        RecyclerView focusList = findViewById(R.id.FocusList);
        focusList.setLayoutManager(new LinearLayoutManager(this));
        focusList.setAdapter(focusAdapter);
        focusList.setNestedScrollingEnabled(false);

        obligationAdapter = new ObligationAdapter(new ObligationAdapter.Listener() {
            @Override public void onComplete(Obligation obligation) { complete(obligation); }
            @Override public void onStepChanged(Obligation obligation, PlanStep step, boolean completed) {
                setStepCompleted(obligation, step, completed);
            }
            @Override public void onMove(Obligation obligation, PlanMove move) {
                repository.move(obligation.id, move, MainActivity.this::reload);
            }
            @Override public void onEdit(Obligation obligation) { showEditor(obligation.isRoutine(), obligation); }
        });
        RecyclerView obligations = findViewById(R.id.ObligationList);
        obligations.setLayoutManager(new LinearLayoutManager(this));
        obligations.setAdapter(obligationAdapter);
        obligations.setNestedScrollingEnabled(false);

        findViewById(R.id.AddTask).setOnClickListener(view -> showEditor(false, null));
        findViewById(R.id.AddRoutine).setOnClickListener(view -> showEditor(true, null));
        findViewById(R.id.AiBulkEdit).setOnClickListener(view -> showAiDialog());
        findViewById(R.id.SelectModel).setOnClickListener(view -> selectModel());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            calendarPermission.launch(Manifest.permission.READ_CALENDAR);
        }
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repository != null) reload();
    }

    private void reload() {
        repository.loadDashboard(3, result -> {
            dashboard = result;
            focusAdapter.submit(result.focus());
            obligationAdapter.submit(result.obligations());
            emptyFocus.setVisibility(result.focus().isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void complete(Obligation obligation) {
        repository.complete(obligation.id, completed -> {
            if (completed == null) return;
            celebration.burst();
            celebration.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            String message = completed.isRoutine() && completed.currentStreak > 0
                    ? "Erledigt · 🔥 " + completed.currentStreak
                    : "Erledigt";
            Snackbar.make(findViewById(R.id.Root), message, Snackbar.LENGTH_SHORT).show();
            reload();
        });
    }

    private void setStepCompleted(Obligation obligation, PlanStep step, boolean completed) {
        repository.setStepCompleted(obligation.id, step.id(), completed, updated -> {
            if (updated == null) {
                reload();
                return;
            }
            if (!updated.isOpenOn(LocalDate.now())) {
                celebration.burst();
                celebration.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                String message = updated.currentStreak > 0
                        ? "Routine erledigt · 🔥 " + updated.currentStreak
                        : "Routine erledigt";
                Snackbar.make(findViewById(R.id.Root), message, Snackbar.LENGTH_SHORT).show();
            }
            reload();
        });
    }

    private void showEditor(boolean routine, Obligation existing) {
        View view = getLayoutInflater().inflate(R.layout.dialog_obligation, null);
        EditText title = view.findViewById(R.id.EditTitle);
        EditText duration = view.findViewById(R.id.EditDuration);
        EditText deadline = view.findViewById(R.id.EditDeadline);
        EditText cadence = view.findViewById(R.id.EditCadence);
        EditText nextDue = view.findViewById(R.id.EditNextDue);
        EditText steps = view.findViewById(R.id.EditSteps);
        Spinner timePreference = view.findViewById(R.id.EditTimePreference);
        View taskFields = view.findViewById(R.id.TaskFields);
        View routineFields = view.findViewById(R.id.RoutineFields);
        taskFields.setVisibility(routine ? View.GONE : View.VISIBLE);
        routineFields.setVisibility(routine ? View.VISIBLE : View.GONE);

        Obligation source = existing != null ? existing : new Obligation();
        title.setText(source.title);
        duration.setText(Integer.toString(source.durationMinutes));
        deadline.setText(source.deadlineAt == null ? "" : source.deadlineAt.format(INPUT_DATE_TIME));
        cadence.setText(Integer.toString(source.cadenceDays > 0 ? source.cadenceDays : routine ? 1 : 0));
        nextDue.setText(source.nextDueDate == null ? LocalDate.now().toString() : source.nextDueDate.toString());
        steps.setText(formatSteps(source.steps));
        timePreference.setSelection(timePreferenceSelection(source.timePreference));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(routine ? R.string.edit_routine : R.string.edit_task)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(existing == null ? R.string.empty : R.string.delete, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                try {
                    Obligation edited = copy(source);
                    edited.kind = routine ? Obligation.Kind.ROUTINE : Obligation.Kind.TASK;
                    edited.title = required(title, "Titel");
                    edited.durationMinutes = parseInt(duration, "Dauer", 5, 480);
                    edited.timePreference = selectedTimePreference(timePreference.getSelectedItemPosition());
                    if (routine) {
                        edited.cadenceDays = parseInt(cadence, "Rhythmus", 1, 365);
                        edited.nextDueDate = LocalDate.parse(required(nextDue, "Nächste Fälligkeit"));
                        edited.deadlineAt = null;
                        edited.steps = parseSteps(steps.getText().toString(), source.steps);
                    } else {
                        edited.deadlineAt = deadline.getText().toString().trim().isEmpty()
                                ? null
                                : LocalDateTime.parse(deadline.getText().toString().trim(), INPUT_DATE_TIME);
                        edited.cadenceDays = 0;
                        edited.nextDueDate = null;
                        edited.steps.clear();
                    }
                    repository.save(edited, this::reload);
                    dialog.dismiss();
                } catch (RuntimeException error) {
                    Snackbar.make(view, error.getMessage(), Snackbar.LENGTH_LONG).show();
                }
            });
            Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (existing == null) {
                neutral.setVisibility(View.GONE);
            } else {
                neutral.setOnClickListener(button -> confirmDelete(existing, dialog));
            }
        });
        dialog.show();
    }

    private void confirmDelete(Obligation existing, AlertDialog editor) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_delete, existing.title))
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        repository.delete(existing.id, () -> {
                            editor.dismiss();
                            reload();
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAiDialog() {
        if (!bulkEditor.hasModel()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.local_ai)
                    .setMessage(R.string.model_needed_explanation)
                    .setPositiveButton(R.string.select_model, (dialog, which) -> selectModel())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_ai, null);
        EditText instruction = view.findViewById(R.id.AiInstruction);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.ai_bulk_title)
                .setView(view)
                .setPositiveButton(R.string.create_preview, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String command = instruction.getText().toString().trim();
                    if (command.isEmpty()) return;
                    button.setEnabled(false);
                    ((TextView) button).setText(R.string.ai_working);
                    bulkEditor.propose(
                            command,
                            dashboard.obligations(),
                            proposal -> runOnUiThread(() -> {
                                dialog.dismiss();
                                showProposal(proposal);
                            }),
                            error -> runOnUiThread(() -> {
                                button.setEnabled(true);
                                ((TextView) button).setText(R.string.create_preview);
                                showError(error);
                            }));
                }));
        dialog.show();
    }

    private void showProposal(BulkChangeProposal proposal) {
        String details = proposal.previewLines().isEmpty()
                ? "Keine Änderungen vorgeschlagen."
                : String.join("\n\n", proposal.previewLines());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_changes)
                .setMessage(proposal.summary() + "\n\n" + details)
                .setPositiveButton(R.string.apply_changes, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(!proposal.previewLines().isEmpty());
            positive.setOnClickListener(view -> repository.apply(
                    proposal.upserts(), proposal.deletions(), () -> {
                        dialog.dismiss();
                        Snackbar.make(findViewById(R.id.Root), R.string.changes_applied, Snackbar.LENGTH_SHORT).show();
                        reload();
                    }));
        });
        dialog.show();
    }

    private void selectModel() {
        modelPicker.launch(new String[]{"application/octet-stream", "*/*"});
    }

    private List<RoutineStep> parseSteps(String value, List<RoutineStep> existing) {
        List<RoutineStep> result = new ArrayList<>();
        for (String rawLine : value.split("\\R")) {
            if (rawLine.trim().isEmpty()) continue;
            String[] parts = rawLine.split("\\|", 2);
            String title = parts[0].trim();
            if (title.isEmpty()) continue;
            EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            if (parts.length == 2) {
                for (String token : parts[1].split(",")) {
                    DayOfWeek day = DAY_NAMES.get(token.trim().toLowerCase(Locale.GERMAN));
                    if (day == null) throw new IllegalArgumentException("Unbekannter Wochentag: " + token.trim());
                    days.add(day);
                }
            }
            int index = result.size();
            if (existing != null && index < existing.size()) {
                RoutineStep previous = existing.get(index);
                result.add(new RoutineStep(
                        previous.id, title, days, previous.completedFor, previous.completedAt));
            } else {
                result.add(new RoutineStep(title, days));
            }
        }
        return result;
    }

    private int timePreferenceSelection(TimePreference preference) {
        if (preference == null) return 0;
        return switch (preference) {
            case MORNING -> 1;
            case MIDDAY -> 2;
            case EVENING -> 3;
        };
    }

    private TimePreference selectedTimePreference(int selection) {
        return switch (selection) {
            case 1 -> TimePreference.MORNING;
            case 2 -> TimePreference.MIDDAY;
            case 3 -> TimePreference.EVENING;
            default -> null;
        };
    }

    private String formatSteps(List<RoutineStep> steps) {
        List<String> lines = new ArrayList<>();
        for (RoutineStep step : steps) {
            String line = step.title;
            if (!step.days.isEmpty()) {
                line += " | " + step.days.stream().map(DAY_LABELS::get).reduce((a, b) -> a + "," + b).orElse("");
            }
            lines.add(line);
        }
        return String.join("\n", lines);
    }

    private Obligation copy(Obligation source) {
        Obligation copy = new Obligation();
        copy.id = source.id;
        copy.kind = source.kind;
        copy.title = source.title;
        copy.durationMinutes = source.durationMinutes;
        copy.deadlineAt = source.deadlineAt;
        copy.cadenceDays = source.cadenceDays;
        copy.nextDueDate = source.nextDueDate;
        copy.timePreference = source.timePreference;
        copy.steps = source.steps.stream().map(RoutineStep::copy).collect(java.util.stream.Collectors.toList());
        copy.createdAt = source.createdAt;
        copy.completed = source.completed;
        copy.currentStreak = source.currentStreak;
        copy.bestStreak = source.bestStreak;
        copy.totalCompletions = source.totalCompletions;
        copy.manualOrderOn = source.manualOrderOn;
        copy.manualOrderRank = source.manualOrderRank;
        return copy;
    }

    private String required(EditText field, String label) {
        String value = field.getText().toString().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(label + " fehlt");
        return value;
    }

    private int parseInt(EditText field, String label, int min, int max) {
        try {
            int value = Integer.parseInt(required(field, label));
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + ": " + min + "–" + max);
        }
    }

    private void showError(Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        new AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static final DateTimeFormatter INPUT_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Map<String, DayOfWeek> DAY_NAMES = new HashMap<>();
    private static final Map<DayOfWeek, String> DAY_LABELS = new HashMap<>();

    static {
        String[] labels = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
        DayOfWeek[] days = DayOfWeek.values();
        for (int index = 0; index < days.length; index++) {
            DAY_NAMES.put(labels[index].toLowerCase(Locale.GERMAN), days[index]);
            DAY_LABELS.put(days[index], labels[index]);
        }
    }
}
