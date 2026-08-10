package com.autosecretary.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.ai.BulkChangeProposal;
import com.autosecretary.ai.OnDeviceBulkEditor;
import com.autosecretary.core.Obligation;
import com.autosecretary.core.CalendarBlock;
import com.autosecretary.core.PlanItem;
import com.autosecretary.core.PlanMove;
import com.autosecretary.core.PlanStep;
import com.autosecretary.core.RoutineStep;
import com.autosecretary.core.TimePreference;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private Dashboard dashboard = new Dashboard(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    private CelebrationView celebration;
    private TextView emptyFocus;
    private TextView modelStatus;
    private TextView allHeading;
    private LinearLayout calendarContext;
    private View calendarPermissionCard;
    private View todayPanel;
    private View allPanel;
    private View aiPanel;
    private DaylightBackdropView daylightBackdrop;
    private String listFilter = "open";
    private boolean modelInstallRunning;

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
            new ActivityResultContracts.RequestPermission(), granted -> {
                updateCalendarPermissionCard();
                reload();
            });

    private final ActivityResultLauncher<String> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) updateLocation();
            });

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
        allHeading = findViewById(R.id.AllHeading);
        calendarContext = findViewById(R.id.CalendarContext);
        calendarPermissionCard = findViewById(R.id.CalendarPermissionCard);
        todayPanel = findViewById(R.id.TodayPanel);
        allPanel = findViewById(R.id.AllPanel);
        aiPanel = findViewById(R.id.AiPanel);
        daylightBackdrop = findViewById(R.id.DaylightBackdrop);
        configureDaylight();
        updateGreeting();
        if (bulkEditor.hasModel()) {
            modelStatus.setText(R.string.model_ready);
        } else if (gemmaTermsAccepted()) {
            installBundledAi(null);
        } else {
            modelStatus.setText(R.string.model_bundled);
        }

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

        findViewById(R.id.AddFab).setOnClickListener(view -> showAddMenu());
        findViewById(R.id.AiBulkEdit).setOnClickListener(view -> ensureAiReady(this::showAiDialog));
        findViewById(R.id.SelectModel).setOnClickListener(view -> selectModel());
        findViewById(R.id.NavToday).setOnClickListener(view -> showSurface("today"));
        findViewById(R.id.NavAll).setOnClickListener(view -> showSurface("all"));
        findViewById(R.id.NavAi).setOnClickListener(view -> showSurface("ai"));
        findViewById(R.id.FilterOpen).setOnClickListener(view -> setListFilter("open"));
        findViewById(R.id.FilterRoutines).setOnClickListener(view -> setListFilter("routines"));
        findViewById(R.id.FilterDone).setOnClickListener(view -> setListFilter("done"));
        findViewById(R.id.ThemeMode).setOnClickListener(view -> cycleThemeMode());
        findViewById(R.id.CalendarPermissionAction).setOnClickListener(view -> requestCalendarAccess());
        updateCalendarPermissionCard();
        showSurface("today");
        setListFilter("open");
        reload();
        scheduleMinuteRefresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repository != null) {
            updateCalendarPermissionCard();
            updateLocation();
            reload();
        }
    }

    private void reload() {
        repository.loadDashboard(3, result -> {
            dashboard = result;
            focusAdapter.submit(result.focus());
            submitFilteredObligations();
            emptyFocus.setVisibility(result.focus().isEmpty() ? View.VISIBLE : View.GONE);
            renderCalendar(result.calendar());
            allHeading.setText("Alles · " + result.obligations().size());
        });
    }

    private void showAddMenu() {
        new AlertDialog.Builder(this)
                .setTitle("Was möchtest du anlegen?")
                .setItems(new String[]{"Aufgabe", "Routine"}, (dialog, which) ->
                        showEditor(which == 1, null))
                .show();
    }

    private void showSurface(String surface) {
        boolean today = "today".equals(surface);
        boolean all = "all".equals(surface);
        todayPanel.setVisibility(today ? View.VISIBLE : View.GONE);
        allPanel.setVisibility(all ? View.VISIBLE : View.GONE);
        aiPanel.setVisibility("ai".equals(surface) ? View.VISIBLE : View.GONE);
        setNavState(R.id.NavToday, today);
        setNavState(R.id.NavAll, all);
        setNavState(R.id.NavAi, "ai".equals(surface));
    }

    private void setNavState(int id, boolean selected) {
        TextView view = findViewById(id);
        view.setTextColor(ContextCompat.getColor(this, selected ? R.color.ink_secondary : R.color.marker));
        view.setPaintFlags(selected
                ? view.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG
                : view.getPaintFlags() & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG);
    }

    private void setListFilter(String filter) {
        listFilter = filter;
        submitFilteredObligations();
        setFilterState(R.id.FilterOpen, "open".equals(filter));
        setFilterState(R.id.FilterRoutines, "routines".equals(filter));
        setFilterState(R.id.FilterDone, "done".equals(filter));
    }

    private void setFilterState(int id, boolean selected) {
        TextView view = findViewById(id);
        view.setTextColor(ContextCompat.getColor(this, selected ? R.color.forest : R.color.marker));
        view.setPaintFlags(selected
                ? view.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG
                : view.getPaintFlags() & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG);
    }

    private void submitFilteredObligations() {
        LocalDate today = LocalDate.now();
        List<Obligation> filtered = dashboard.obligations().stream()
                .filter(item -> switch (listFilter) {
                    case "routines" -> item.isRoutine();
                    case "done" -> !item.isRoutine() && item.completed;
                    default -> item.isOpenOn(today);
                })
                .collect(java.util.stream.Collectors.toList());
        obligationAdapter.submit(filtered);
        if (allHeading != null) allHeading.setText("Alles · " + filtered.size());
    }

    private void renderCalendar(List<CalendarBlock> blocks) {
        calendarContext.removeAllViews();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) return;
        if (blocks.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("●  heute keine Termine im Kalender");
            empty.setTextColor(ContextCompat.getColor(this, R.color.calendar_label));
            empty.setTextSize(15);
            empty.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC);
            empty.setPadding(dp(6), dp(10), dp(6), dp(12));
            calendarContext.addView(empty);
            return;
        }
        DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm");
        for (CalendarBlock block : blocks) {
            View row = getLayoutInflater().inflate(R.layout.row_calendar, calendarContext, false);
            ((TextView) row.findViewById(R.id.CalendarTime)).setText(block.start().format(time));
            ((TextView) row.findViewById(R.id.CalendarTitle)).setText(block.title());
            row.setRotation(calendarContext.getChildCount() % 2 == 0 ? -0.8f : 0.8f);
            calendarContext.addView(row);
        }
    }

    private void updateCalendarPermissionCard() {
        if (calendarPermissionCard == null) return;
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
        calendarPermissionCard.setVisibility(granted ? View.GONE : View.VISIBLE);
        if (!granted) {
            boolean asked = getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
                    .getBoolean(CALENDAR_ASKED, false);
            ((Button) findViewById(R.id.CalendarPermissionAction)).setText(
                    asked && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
                            ? "Einstellungen" : "Kalender freigeben");
        }
    }

    private void requestCalendarAccess() {
        boolean asked = getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
                .getBoolean(CALENDAR_ASKED, false);
        if (asked && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
                .edit().putBoolean(CALENDAR_ASKED, true).apply();
        calendarPermission.launch(Manifest.permission.READ_CALENDAR);
    }

    private void configureDaylight() {
        String stored = getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
                .getString(THEME_MODE, DaylightBackdropView.Mode.AUTO.name());
        DaylightBackdropView.Mode mode;
        try {
            mode = DaylightBackdropView.Mode.valueOf(stored);
        } catch (IllegalArgumentException ignored) {
            mode = DaylightBackdropView.Mode.AUTO;
        }
        daylightBackdrop.setMode(mode);
        ((TextView) findViewById(R.id.ThemeMode)).setText(switch (mode) {
            case AUTO -> "◐";
            case LIGHT -> "☀";
            case DARK -> "☾";
        });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            updateLocation();
        } else if (!getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
                .getBoolean(LOCATION_ASKED, false)) {
            getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
                    .edit().putBoolean(LOCATION_ASKED, true).apply();
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        applyCardMode(mode);
    }

    private void cycleThemeMode() {
        DaylightBackdropView.Mode current = DaylightBackdropView.Mode.valueOf(
                getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
                        .getString(THEME_MODE, DaylightBackdropView.Mode.AUTO.name()));
        DaylightBackdropView.Mode next = switch (current) {
            case AUTO -> DaylightBackdropView.Mode.LIGHT;
            case LIGHT -> DaylightBackdropView.Mode.DARK;
            case DARK -> DaylightBackdropView.Mode.AUTO;
        };
        getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
                .edit().putString(THEME_MODE, next.name()).apply();
        daylightBackdrop.setMode(next);
        applyCardMode(next);
        recreate();
    }

    private void applyCardMode(DaylightBackdropView.Mode mode) {
        int target = switch (mode) {
            case LIGHT -> AppCompatDelegate.MODE_NIGHT_NO;
            case DARK -> AppCompatDelegate.MODE_NIGHT_YES;
            case AUTO -> daylightBackdrop.wantsLightCards()
                    ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        };
        if (getDelegate().getLocalNightMode() != target) getDelegate().setLocalNightMode(target);
    }

    private void updateLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            Location location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (location == null) location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location != null) applyLocation(location);
            if (android.os.Build.VERSION.SDK_INT >= 30
                    && manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, getMainExecutor(),
                        this::applyLocation);
            }
        } catch (SecurityException ignored) {
            // Permission may have been revoked between the check and provider call.
        }
    }

    private void applyLocation(Location location) {
        if (location == null || daylightBackdrop == null) return;
        daylightBackdrop.setCoordinates(location.getLatitude(), location.getLongitude());
        DaylightBackdropView.Mode mode = DaylightBackdropView.Mode.valueOf(
                getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
                        .getString(THEME_MODE, DaylightBackdropView.Mode.AUTO.name()));
        if (mode == DaylightBackdropView.Mode.AUTO) applyCardMode(mode);
    }

    private void updateGreeting() {
        int minute = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
        String greeting = minute < 5 * 60 ? "Noch früh"
                : minute < 9 * 60 ? "Guten Morgen"
                : minute < 12 * 60 ? "Vormittag"
                : minute < 14 * 60 ? "Mittag"
                : minute < 18 * 60 ? "Nachmittag"
                : minute < 21 * 60 ? "Guten Abend"
                : minute < 23 * 60 ? "Es wird spät" : "Gute Nacht";
        ((TextView) findViewById(R.id.Greeting)).setText(greeting);
    }

    private void scheduleMinuteRefresh() {
        View root = findViewById(R.id.Root);
        root.postDelayed(() -> {
            updateGreeting();
            DaylightBackdropView.Mode mode = DaylightBackdropView.Mode.valueOf(
                    getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
                            .getString(THEME_MODE, DaylightBackdropView.Mode.AUTO.name()));
            if (mode == DaylightBackdropView.Mode.AUTO) applyCardMode(mode);
            scheduleMinuteRefresh();
        }, 60_000L);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void complete(Obligation obligation) {
        repository.complete(obligation.id, completed -> {
            if (completed == null) return;
            celebration.burst();
            celebration.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            String message = completed.isRoutine() && completed.currentStreak > 0
                    ? "Geschafft · " + completed.currentStreak + ". Ring"
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
                String message = updated.isRoutine() && updated.currentStreak > 0
                        ? "Routine geschafft · " + updated.currentStreak + ". Ring"
                        : "Aufgabe geschafft";
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
        SwitchMaterial flexible = view.findViewById(R.id.EditFlexible);
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
        deadline.setOnClickListener(ignored -> showDeadlinePicker(deadline));
        nextDue.setOnClickListener(ignored -> showDatePicker(nextDue));
        steps.setText(formatSteps(source.steps));
        timePreference.setSelection(timePreferenceSelection(source.timePreference));
        flexible.setChecked(source.flexible);

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
                    edited.flexible = flexible.isChecked();
                    edited.steps = parseSteps(steps.getText().toString(), source.steps);
                    if (routine) {
                        edited.cadenceDays = parseInt(cadence, "Rhythmus", 1, 365);
                        edited.nextDueDate = LocalDate.parse(required(nextDue, "Nächste Fälligkeit"));
                        edited.deadlineAt = null;
                    } else {
                        edited.deadlineAt = parseDeadline(deadline.getText().toString());
                        if (edited.deadlineAt != null && edited.deadlineAt.isBefore(LocalDateTime.now())) {
                            throw new IllegalArgumentException("Die Deadline liegt in der Vergangenheit.");
                        }
                        edited.cadenceDays = 0;
                        edited.nextDueDate = null;
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

    private void showDeadlinePicker(EditText target) {
        LocalDate initial = LocalDate.now();
        String current = target.getText().toString().trim();
        try {
            if (!current.isEmpty()) initial = parseDeadline(current).toLocalDate();
        } catch (RuntimeException ignored) {
            // Invalid manually restored text falls back to today and will be replaced by the picker.
        }
        LocalDate selectedInitial = initial;
        new android.app.DatePickerDialog(this, (picker, year, month, day) -> {
            LocalDate selected = LocalDate.of(year, month + 1, day);
            new AlertDialog.Builder(this)
                    .setTitle("Deadline am " + selected.format(DateTimeFormatter.ofPattern("dd.MM.")))
                    .setItems(new String[]{"bis Tagesende", "Uhrzeit wählen", "ohne Deadline"},
                            (dialog, which) -> {
                                if (which == 0) target.setText(selected.toString());
                                if (which == 1) new android.app.TimePickerDialog(this,
                                        (timePicker, hour, minute) -> target.setText(
                                                selected.atTime(hour, minute).format(INPUT_DATE_TIME)),
                                        18, 0, true).show();
                                if (which == 2) target.setText("");
                            })
                    .show();
        }, selectedInitial.getYear(), selectedInitial.getMonthValue() - 1, selectedInitial.getDayOfMonth()).show();
    }

    private void showDatePicker(EditText target) {
        LocalDate initial;
        try {
            initial = LocalDate.parse(target.getText().toString().trim());
        } catch (RuntimeException ignored) {
            initial = LocalDate.now();
        }
        new android.app.DatePickerDialog(this, (picker, year, month, day) ->
                target.setText(LocalDate.of(year, month + 1, day).toString()),
                initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    private void showAiDialog() {
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
        String[] changes = proposal.previewLines().toArray(new String[0]);
        boolean[] selected = new boolean[changes.length];
        java.util.Arrays.fill(selected, true);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_changes)
                .setMessage(proposal.summary())
                .setMultiChoiceItems(changes, selected, (ignored, which, checked) -> selected[which] = checked)
                .setPositiveButton(R.string.apply_changes, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(!proposal.previewLines().isEmpty());
            positive.setOnClickListener(view -> {
                List<Obligation> upserts = new ArrayList<>();
                List<String> deletions = new ArrayList<>();
                int upsertIndex = 0;
                int deletionIndex = 0;
                for (int index = 0; index < changes.length; index++) {
                    boolean deletion = changes[index].startsWith("Löschen:");
                    if (selected[index]) {
                        if (deletion) deletions.add(proposal.deletions().get(deletionIndex));
                        else upserts.add(proposal.upserts().get(upsertIndex));
                    }
                    if (deletion) deletionIndex++; else upsertIndex++;
                }
                if (upserts.isEmpty() && deletions.isEmpty()) {
                    Snackbar.make(findViewById(R.id.Root), "Nichts ausgewählt · nichts verändert", Snackbar.LENGTH_SHORT).show();
                    return;
                }
                Map<String, Obligation> beforeById = new HashMap<>();
                for (Obligation item : dashboard.obligations()) beforeById.put(item.id, item);
                List<Obligation> undoUpserts = new ArrayList<>();
                List<String> undoDeletions = new ArrayList<>();
                for (Obligation item : upserts) {
                    Obligation before = beforeById.get(item.id);
                    if (before == null) undoDeletions.add(item.id); else undoUpserts.add(copy(before));
                }
                for (String id : deletions) {
                    Obligation before = beforeById.get(id);
                    if (before != null) undoUpserts.add(copy(before));
                }
                repository.apply(upserts, deletions, () -> {
                        dialog.dismiss();
                        Snackbar.make(findViewById(R.id.Root), R.string.changes_applied, Snackbar.LENGTH_LONG)
                                .setAction("zurücknehmen", undo -> repository.apply(
                                        undoUpserts, undoDeletions, this::reload))
                                .show();
                        reload();
                    });
            });
        });
        dialog.show();
    }

    private void selectModel() {
        modelPicker.launch(new String[]{"application/octet-stream", "*/*"});
    }

    private void ensureAiReady(Runnable continuation) {
        if (bulkEditor.hasModel()) {
            continuation.run();
            return;
        }
        if (!gemmaTermsAccepted()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.gemma_terms_title)
                    .setMessage(R.string.gemma_terms_message)
                    .setPositiveButton(R.string.accept, (dialog, which) -> {
                        getSharedPreferences(AI_PREFERENCES, MODE_PRIVATE)
                                .edit().putBoolean(GEMMA_TERMS_ACCEPTED, true).apply();
                        installBundledAi(continuation);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        installBundledAi(continuation);
    }

    private void installBundledAi(Runnable continuation) {
        if (modelInstallRunning) {
            Toast.makeText(this, R.string.model_importing, Toast.LENGTH_SHORT).show();
            return;
        }
        modelInstallRunning = true;
        modelStatus.setText(R.string.model_importing);
        bulkEditor.installBundledModel(
                () -> runOnUiThread(() -> {
                    modelInstallRunning = false;
                    modelStatus.setText(R.string.model_ready);
                    if (continuation != null) continuation.run();
                }),
                error -> runOnUiThread(() -> {
                    modelInstallRunning = false;
                    modelStatus.setText(R.string.model_missing);
                    showError(error);
                }));
    }

    private boolean gemmaTermsAccepted() {
        return getSharedPreferences(AI_PREFERENCES, MODE_PRIVATE)
                .getBoolean(GEMMA_TERMS_ACCEPTED, false);
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
        copy.flexible = source.flexible;
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

    private LocalDateTime parseDeadline(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return null;
        if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(value).atTime(23, 59);
        }
        return LocalDateTime.parse(value, INPUT_DATE_TIME);
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
    private static final String AI_PREFERENCES = "local_ai";
    private static final String GEMMA_TERMS_ACCEPTED = "gemma_terms_accepted";
    private static final String UI_PREFERENCES = "waldmorgen_ui";
    private static final String THEME_MODE = "theme_mode";
    private static final String LOCATION_ASKED = "location_asked";
    private static final String CALENDAR_ASKED = "calendar_asked";
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
