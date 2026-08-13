package com.autosecretary.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.BuildConfig;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.databinding.ActivityMainBinding;
import com.autosecretary.databinding.RowCalendarBinding;
import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.application.MigrationReview;
import com.autosecretary.domain.PlanConflict;
import com.autosecretary.ui.editor.ObligationEditorDialogFragment;
import com.autosecretary.ui.settings.PlanningSettingsDialogFragment;
import com.autosecretary.ui.ai.AiUiState;
import com.autosecretary.ui.ai.AiViewModel;
import com.autosecretary.ui.ai.AiInstructionDialogFragment;
import com.autosecretary.ui.ai.AiProposalDialogFragment;
import com.autosecretary.ui.ai.AiTermsDialogFragment;
import com.autosecretary.ui.editor.AddWorkItemDialogFragment;
import com.autosecretary.ui.update.UpdateUiState;
import com.autosecretary.ui.update.UpdateViewModel;
import com.autosecretary.ui.migration.LegacyImportDialogFragment;
import com.autosecretary.ui.migration.LegacyImportViewModel;
import com.autosecretary.ui.migration.MigrationReviewDialogFragment;
import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/** One screen: focus anchor, complete list, direct editors and confirmed local-AI bulk changes. */
public final class MainActivity extends AppCompatActivity implements
        LegacyImportDialogFragment.Host, ObligationEditorDialogFragment.Host,
        PlanningSettingsDialogFragment.Host, MigrationReviewDialogFragment.Host,
        AddWorkItemDialogFragment.Host, AiInstructionDialogFragment.Host,
        AiProposalDialogFragment.Host, AiTermsDialogFragment.Host {
    private MainViewModel viewModel;
    private UpdateViewModel updateViewModel;
    private AiViewModel aiViewModel;
    private FocusAdapter focusAdapter;
    private ObligationAdapter obligationAdapter;
    private Dashboard dashboard = new Dashboard(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    private CelebrationView celebration;
    private TextView emptyFocus;
    private TextView modelStatus;
    private TextView allHeading;
    private TextView undoAction;
    private TextView updateStatus;
    private TextView planningConflicts;
    private android.widget.Button updateAction;
    private LinearLayout calendarContext;
    private View calendarPermissionCard;
    private View todayPanel;
    private View allPanel;
    private View aiPanel;
    private DaylightController daylightController;
    private String listFilter = "open";
    private long renderedCompletionSignal;
    private long renderedPlanningSettingsSignal;
    private Bundle creationState;
    private View root;
    private ActivityMainBinding binding;
    private LegacyImportViewModel legacyImportViewModel;

    private final ActivityResultLauncher<String[]> modelPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null || aiViewModel == null) return;
                android.content.Context app = getApplicationContext();
                aiViewModel.importModel(() -> app.getContentResolver().openInputStream(uri));
            });

    private final ActivityResultLauncher<String> calendarPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (viewModel == null) return;
                AutoSecretaryApplication.from(this).refreshCalendarObservation();
                updateCalendarPermissionCard();
                reload();
            });

    private final ActivityResultLauncher<String> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted ->
                    { if (daylightController != null) daylightController.onLocationPermissionResult(granted); });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        creationState = savedInstanceState;
        if (savedInstanceState != null) {
            renderedCompletionSignal = savedInstanceState.getLong(RENDERED_COMPLETION_SIGNAL);
            renderedPlanningSettingsSignal = savedInstanceState.getLong(
                    RENDERED_PLANNING_SETTINGS_SIGNAL);
        }
        root = binding.Root;
        AutoSecretaryApplication app = AutoSecretaryApplication.from(this);
        root.setContentDescription("Auto Secretary · Initialisierung");
        legacyImportViewModel = new ViewModelProvider(this,
                app.legacyImportViewModelFactory()).get(LegacyImportViewModel.class);
        if (app.legacyImports().requiresUserDecision()) {
            if (getSupportFragmentManager().findFragmentByTag(LegacyImportDialogFragment.TAG) == null) {
                new LegacyImportDialogFragment().show(
                        getSupportFragmentManager(), LegacyImportDialogFragment.TAG);
            }
            return;
        }
        initializeCore(app);
    }

    private void initializeCore(AutoSecretaryApplication app) {
        if (viewModel != null) return;
        viewModel = new ViewModelProvider(this,
                app.mainViewModelFactory(this, creationState))
                .get(MainViewModel.class);
        updateViewModel = new ViewModelProvider(this, app.updateViewModelFactory())
                .get(UpdateViewModel.class);
        aiViewModel = new ViewModelProvider(this, app.aiViewModelFactory())
                .get(AiViewModel.class);
        celebration = binding.Celebration;
        emptyFocus = binding.EmptyFocus;
        modelStatus = binding.ModelStatus;
        allHeading = binding.AllHeading;
        undoAction = binding.UndoAction;
        updateStatus = binding.UpdateStatus;
        planningConflicts = binding.PlanningConflicts;
        updateAction = binding.UpdateAction;
        calendarContext = binding.CalendarContext;
        calendarPermissionCard = binding.CalendarPermissionCard;
        todayPanel = binding.TodayPanel;
        allPanel = binding.AllPanel;
        aiPanel = binding.AiPanel;
        daylightController = new DaylightController(this, root,
                binding.DaylightBackdrop, binding.ThemeMode, binding.Greeting,
                app.location(),
                () -> locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION));
        daylightController.configure();

        focusAdapter = new FocusAdapter(new FocusAdapter.Listener() {
            @Override public void onComplete(String id) { complete(id); }
            @Override public void onStepChanged(String id, String stepId, boolean completed) {
                setStepCompleted(id, stepId, completed);
            }
            @Override public void onMove(String id, MoveWorkItemUseCase.Direction direction) {
                viewModel.move(id, direction);
            }
        });
        RecyclerView focusList = binding.FocusList;
        focusList.setLayoutManager(new LinearLayoutManager(this));
        focusList.setAdapter(focusAdapter);
        focusList.setNestedScrollingEnabled(false);

        obligationAdapter = new ObligationAdapter(new ObligationAdapter.Listener() {
            @Override public void onComplete(String id) { complete(id); }
            @Override public void onMove(String id, MoveWorkItemUseCase.Direction direction) {
                viewModel.move(id, direction);
            }
            @Override public void onEdit(String id, boolean routine) {
                viewModel.openEditor(routine, id);
            }
        });
        RecyclerView obligations = binding.ObligationList;
        obligations.setLayoutManager(new LinearLayoutManager(this));
        obligations.setAdapter(obligationAdapter);
        obligations.setNestedScrollingEnabled(false);

        binding.AddFab.setOnClickListener(view -> showAddMenu());
        binding.AiBulkEdit.setOnClickListener(view -> ensureAiReady(this::showAiInstruction));
        binding.SelectModel.setOnClickListener(view -> selectModel());
        binding.PlanningSettings.setOnClickListener(
                view -> viewModel.openPlanningSettings());
        updateAction.setOnClickListener(view -> handleUpdateAction());
        binding.NavToday.setOnClickListener(view -> viewModel.selectSurface("today"));
        binding.NavAll.setOnClickListener(view -> viewModel.selectSurface("all"));
        binding.NavAi.setOnClickListener(view -> viewModel.selectSurface("ai"));
        binding.FilterOpen.setOnClickListener(view -> viewModel.selectFilter("open"));
        binding.FilterRoutines.setOnClickListener(view -> viewModel.selectFilter("routines"));
        binding.FilterDone.setOnClickListener(view -> viewModel.selectFilter("done"));
        binding.ThemeMode.setOnClickListener(view -> daylightController.cycleMode());
        undoAction.setOnClickListener(view -> viewModel.undo());
        binding.CalendarPermissionAction.setOnClickListener(view -> requestCalendarAccess());
        updateCalendarPermissionCard();
        viewModel.state().observe(this, this::render);
        updateViewModel.state().observe(this, this::renderUpdate);
        aiViewModel.state().observe(this, this::renderAi);
        if (!aiViewModel.state().getValue().modelReady() && aiViewModel.termsAccepted()) {
            aiViewModel.installBundledModel(false);
        }
        if (BuildConfig.DEBUG) {
            updateStatus.setText("Preview-Build · Produktionsupdates sind deaktiviert");
            updateAction.setVisibility(View.GONE);
        } else if (updateViewModel.state().getValue() == null
                || !updateViewModel.state().getValue().checked()) {
            updateViewModel.check();
        }
        if (getLifecycle().getCurrentState().isAtLeast(
                androidx.lifecycle.Lifecycle.State.STARTED)) {
            daylightController.onStart();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (daylightController != null) daylightController.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            AutoSecretaryApplication.from(this).refreshCalendarObservation();
            updateCalendarPermissionCard();
            reload();
        }
    }

    @Override
    protected void onStop() {
        if (daylightController != null) daylightController.onStop();
        super.onStop();
    }

    private void reload() {
        viewModel.reload();
    }

    private void render(MainUiState state) {
        if (state.dashboard() != null) {
            root.setContentDescription("Auto Secretary · Datenbank "
                    + AutoSecretaryApplication.from(this).databaseVersion());
        }
        showSurface(state.surface());
        setListFilter(state.filter());
        if (state.dashboard() != null) {
            dashboard = UiModelMapper.dashboard(state.dashboard(), LocalDate.now());
            focusAdapter.submit(dashboard.focus());
            submitFilteredObligations();
            emptyFocus.setVisibility(dashboard.focus().isEmpty() ? View.VISIBLE : View.GONE);
            renderCalendar(dashboard.calendar());
            allHeading.setText("Alles · " + dashboard.workItems().size());
            String undoLabel = state.dashboard().undoLabel();
            undoAction.setVisibility(undoLabel == null ? View.GONE : View.VISIBLE);
            undoAction.setContentDescription(undoLabel);
            showMigrationReview(state.dashboard().migrationReview());
            renderPlanningConflicts(state.dashboard().conflicts());
            renderEditor(state);
        }
        renderPlanningSettings(state);
        if (state.planningSettingsSignal() > renderedPlanningSettingsSignal) {
            renderedPlanningSettingsSignal = state.planningSettingsSignal();
            AutoSecretaryApplication.from(this).scheduleBackground();
        }
        if (state.completionSignal() > renderedCompletionSignal) {
            renderedCompletionSignal = state.completionSignal();
            celebration.burst();
            celebration.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                    ? HapticFeedbackConstants.CONFIRM
                    : HapticFeedbackConstants.KEYBOARD_TAP);
            Snackbar.make(binding.Root, "Erledigt", Snackbar.LENGTH_SHORT).show();
        }
        if (state.error() != null && !state.error().isBlank()) {
            Toast.makeText(this, state.error(), Toast.LENGTH_LONG).show();
            viewModel.consumeError();
        }
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        outState.putLong(RENDERED_COMPLETION_SIGNAL, renderedCompletionSignal);
        outState.putLong(RENDERED_PLANNING_SETTINGS_SIGNAL, renderedPlanningSettingsSignal);
        super.onSaveInstanceState(outState);
    }

    private void renderEditor(MainUiState state) {
        if (state.editor() == null) return;
        if (getSupportFragmentManager().findFragmentByTag(
                ObligationEditorDialogFragment.TAG) == null
                && !getSupportFragmentManager().isStateSaved()) {
            new ObligationEditorDialogFragment().show(
                    getSupportFragmentManager(), ObligationEditorDialogFragment.TAG);
        }
    }

    private void renderPlanningSettings(MainUiState state) {
        if (state.planningEditor() == null) return;
        if (getSupportFragmentManager().findFragmentByTag(
                PlanningSettingsDialogFragment.TAG) == null
                && !getSupportFragmentManager().isStateSaved()) {
            new PlanningSettingsDialogFragment().show(
                    getSupportFragmentManager(), PlanningSettingsDialogFragment.TAG);
        }
    }

    @Override
    public LegacyImportViewModel legacyImportViewModel() {
        return legacyImportViewModel;
    }

    @Override
    public void onLegacyImportReady() {
        initializeCore(AutoSecretaryApplication.from(this));
    }

    @Override
    public MainViewModel mainViewModel() { return viewModel; }

    @Override
    public void shareMigrationBackup() {
        java.io.File backup = AutoSecretaryApplication.from(this)
                .migrationBackupArchive();
        if (backup == null || !backup.isFile()) {
            showError("Migrationsbackup wurde nicht gefunden");
            return;
        }
        Uri uri = FileProvider.getUriForFile(this,
                BuildConfig.APPLICATION_ID + ".files", backup);
        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Migrationsbackup sichern"));
    }

    @Override
    public AiViewModel aiViewModel() { return aiViewModel; }

    private void renderPlanningConflicts(List<PlanConflict> conflicts) {
        if (conflicts.isEmpty()) {
            planningConflicts.setVisibility(View.GONE);
            return;
        }
        StringBuilder message = new StringBuilder("Nicht eingeplant:");
        conflicts.stream().limit(3).forEach(conflict -> message.append("\n• ")
                .append(conflict.workItem().title()).append(" — ")
                .append(switch (conflict.reason()) {
                    case AFTER_DEADLINE -> "passt vor der Deadline in kein freies Fenster";
                    case NO_CAPACITY -> "kein ausreichend langes freies Fenster";
                    case OUTSIDE_HORIZON -> "liegt außerhalb des Planungshorizonts";
                }));
        if (conflicts.size() > 3) message.append("\n• und ").append(conflicts.size() - 3)
                .append(" weitere");
        planningConflicts.setText(message.toString());
        planningConflicts.setVisibility(View.VISIBLE);
    }

    private void showMigrationReview(MigrationReview review) {
        if (review == null || getSupportFragmentManager().findFragmentByTag(
                MigrationReviewDialogFragment.TAG) != null
                || getSupportFragmentManager().isStateSaved()) return;
        new MigrationReviewDialogFragment().show(
                getSupportFragmentManager(), MigrationReviewDialogFragment.TAG);
    }

    private void handleUpdateAction() {
        UpdateUiState state = updateViewModel.state().getValue();
        if (state == null || state.busy()) return;
        if (state.verified() != null) {
            startActivity(AutoSecretaryApplication.from(this)
                    .updateIntent(this, state.verified()));
        } else if (state.available() != null) {
            updateViewModel.download();
        } else {
            updateViewModel.check();
        }
    }

    private void renderUpdate(UpdateUiState state) {
        if (BuildConfig.DEBUG || state == null) return;
        updateAction.setEnabled(!state.busy());
        if (state.busy()) {
            updateStatus.setText(state.available() == null
                    ? "Suche nach Produktionsupdate …" : "Update wird geladen und geprüft …");
            return;
        }
        if (state.error() != null) {
            updateStatus.setText(state.error());
            updateAction.setText("Erneut versuchen");
        } else if (state.verified() != null) {
            updateStatus.setText("Version " + state.verified().info().versionName()
                    + " ist signiert und bereit");
            updateAction.setText("System-Installer öffnen");
        } else if (state.available() != null) {
            updateStatus.setText("Version " + state.available().versionName() + " ist verfügbar");
            updateAction.setText("Update laden und prüfen");
        } else if (state.checked()) {
            updateStatus.setText("Diese Version ist aktuell");
            updateAction.setText("Erneut prüfen");
        } else {
            updateStatus.setText("Signierte Produktionsupdates");
            updateAction.setText("Nach Updates suchen");
        }
    }

    private void renderAi(AiUiState state) {
        if (state == null) return;
        if (state.busy()) {
            modelStatus.setText(switch (state.operation()) {
                case INSTALL, IMPORT -> R.string.model_importing;
                case INFERENCE -> R.string.ai_working;
                case NONE -> R.string.model_importing;
            });
        } else {
            modelStatus.setText(state.modelReady() ? R.string.model_ready
                    : BuildConfig.BUNDLED_MODEL ? R.string.model_bundled : R.string.model_missing);
        }
        if (state.error() != null) {
            showError(state.error());
            aiViewModel.consumeError();
            return;
        }
        if (state.openEditorId() > 0) {
            aiViewModel.consumeOpenEditor();
            showAiInstruction();
        }
        if (state.proposal() != null
                && getSupportFragmentManager().findFragmentByTag(
                AiProposalDialogFragment.TAG) == null
                && !getSupportFragmentManager().isStateSaved()) {
            new AiProposalDialogFragment().show(
                    getSupportFragmentManager(), AiProposalDialogFragment.TAG);
        }
    }

    private void showAddMenu() {
        if (getSupportFragmentManager().findFragmentByTag(AddWorkItemDialogFragment.TAG) == null
                && !getSupportFragmentManager().isStateSaved()) {
            new AddWorkItemDialogFragment().show(
                    getSupportFragmentManager(), AddWorkItemDialogFragment.TAG);
        }
    }

    private void showSurface(String surface) {
        boolean today = "today".equals(surface);
        boolean all = "all".equals(surface);
        todayPanel.setVisibility(today ? View.VISIBLE : View.GONE);
        allPanel.setVisibility(all ? View.VISIBLE : View.GONE);
        aiPanel.setVisibility("ai".equals(surface) ? View.VISIBLE : View.GONE);
        setNavState(binding.NavToday, today);
        setNavState(binding.NavAll, all);
        setNavState(binding.NavAi, "ai".equals(surface));
    }

    private void setNavState(TextView view, boolean selected) {
        view.setTextColor(ContextCompat.getColor(this, selected ? R.color.ink_secondary : R.color.marker));
        view.setPaintFlags(selected
                ? view.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG
                : view.getPaintFlags() & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG);
    }

    private void setListFilter(String filter) {
        listFilter = filter;
        submitFilteredObligations();
        setFilterState(binding.FilterOpen, "open".equals(filter));
        setFilterState(binding.FilterRoutines, "routines".equals(filter));
        setFilterState(binding.FilterDone, "done".equals(filter));
    }

    private void setFilterState(TextView view, boolean selected) {
        view.setTextColor(ContextCompat.getColor(this, selected ? R.color.forest : R.color.marker));
        view.setPaintFlags(selected
                ? view.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG
                : view.getPaintFlags() & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG);
    }

    private void submitFilteredObligations() {
        List<WorkItemRow> filtered = dashboard.workItems().stream()
                .filter(item -> switch (listFilter) {
                    case "routines" -> item.routine();
                    case "done" -> !item.routine() && item.completed();
                    default -> item.open();
                })
                .collect(java.util.stream.Collectors.toList());
        obligationAdapter.submit(filtered);
        if (allHeading != null) allHeading.setText("Alles · " + filtered.size());
    }

    private void renderCalendar(List<CalendarRow> blocks) {
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
        for (CalendarRow block : blocks) {
            RowCalendarBinding row = RowCalendarBinding.inflate(
                    getLayoutInflater(), calendarContext, false);
            row.CalendarTime.setText(block.start().format(time));
            row.CalendarTitle.setText(block.title());
            row.getRoot().setRotation(calendarContext.getChildCount() % 2 == 0 ? -0.8f : 0.8f);
            calendarContext.addView(row.getRoot());
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
            binding.CalendarPermissionAction.setText(
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void complete(String id) {
        viewModel.complete(id);
    }

    private void setStepCompleted(String id, String stepId, boolean completed) {
        viewModel.setStepCompleted(id, stepId, completed);
    }

    private void showAiInstruction() {
        if (getSupportFragmentManager().findFragmentByTag(AiInstructionDialogFragment.TAG) == null
                && !getSupportFragmentManager().isStateSaved()) {
            new AiInstructionDialogFragment().show(
                    getSupportFragmentManager(), AiInstructionDialogFragment.TAG);
        }
    }

    private void selectModel() {
        modelPicker.launch(new String[]{"application/octet-stream", "*/*"});
    }

    private void ensureAiReady(Runnable continuation) {
        AiUiState state = aiViewModel.state().getValue();
        if (state != null && state.modelReady()) {
            continuation.run();
            return;
        }
        if (!aiViewModel.termsAccepted()) {
            if (getSupportFragmentManager().findFragmentByTag(AiTermsDialogFragment.TAG) == null
                    && !getSupportFragmentManager().isStateSaved()) {
                new AiTermsDialogFragment().show(
                        getSupportFragmentManager(), AiTermsDialogFragment.TAG);
            }
            return;
        }
        aiViewModel.installBundledModel(true);
    }

    private void showError(String message) {
        if (getSupportFragmentManager().findFragmentByTag(ErrorDialogFragment.TAG) == null
                && !getSupportFragmentManager().isStateSaved()) {
            ErrorDialogFragment.create(message).show(
                    getSupportFragmentManager(), ErrorDialogFragment.TAG);
        }
    }

    private static final String UI_PREFERENCES = "waldmorgen_ui";
    private static final String CALENDAR_ASKED = "calendar_asked";
    private static final String RENDERED_COMPLETION_SIGNAL = "renderedCompletionSignal";
    private static final String RENDERED_PLANNING_SETTINGS_SIGNAL =
            "renderedPlanningSettingsSignal";
}
