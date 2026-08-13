package com.autosecretary.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.BuildConfig;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.databinding.ActivityMainBinding;
import com.autosecretary.databinding.RowCalendarBinding;
import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.domain.PlanConflict;
import com.autosecretary.platform.CalendarChangeObserver;
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
import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One screen: focus anchor, complete list, direct editors and confirmed local-AI bulk changes. */
public final class MainActivity extends AppCompatActivity implements
        ObligationEditorDialogFragment.Host, PlanningSettingsDialogFragment.Host,
        AddWorkItemDialogFragment.Host, AiInstructionDialogFragment.Host,
        AiProposalDialogFragment.Host, AiTermsDialogFragment.Host {
    private MainViewModel viewModel;
    private UpdateViewModel updateViewModel;
    private AiViewModel aiViewModel;
    private FocusAdapter focusAdapter;
    private FocusAdapter laterFocusAdapter;
    private ObligationAdapter obligationAdapter;
    private Dashboard dashboard = new Dashboard(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    private CelebrationView celebration;
    private View emptyFocus;
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
    private CalendarChangeObserver calendarObserver;
    private String listFilter = "open";
    private String currentSurface = "today";
    private long renderedCompletionSignal;
    private Bundle creationState;
    private View root;
    private ActivityMainBinding binding;
    private boolean calendarCardDismissed;

    private final ActivityResultLauncher<String> calendarPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (viewModel == null) return;
                if (calendarObserver != null) calendarObserver.start();
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
        }
        root = binding.Root;
        AutoSecretaryApplication app = AutoSecretaryApplication.from(this);
        root.setContentDescription("Auto Secretary · Initialisierung");
        initializeCore(app);
    }

    private void initializeCore(AutoSecretaryApplication app) {
        if (viewModel != null) return;
        ViewModelProvider.Factory factory = app.viewModelFactory(this, creationState);
        viewModel = new ViewModelProvider(this, factory)
                .get(MainViewModel.class);
        updateViewModel = new ViewModelProvider(this, factory)
                .get(UpdateViewModel.class);
        aiViewModel = new ViewModelProvider(this, factory)
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
                () -> locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION),
                this::refreshEveningPalette);
        calendarObserver = new CalendarChangeObserver(this, app.executors().main(), () -> {
            reload();
            app.graph().refreshWidgets();
        });
        daylightController.configure();

        FocusAdapter.Listener focusListener = new FocusAdapter.Listener() {
            @Override public void onComplete(String id) { complete(id); }
            @Override public void onStepChanged(String id, String stepId, boolean completed) {
                setStepCompleted(id, stepId, completed);
            }
            @Override public void onMove(String id, MoveWorkItemUseCase.Direction direction) {
                viewModel.move(id, direction);
                Snackbar.make(binding.Root, "Reihenfolge geändert", Snackbar.LENGTH_LONG)
                        .setAction("zurücknehmen", view -> viewModel.undo()).show();
            }
            @Override public void onOmit(String id) { omitToday(id); }
        };
        focusAdapter = new FocusAdapter(focusListener);
        laterFocusAdapter = new FocusAdapter(focusListener);
        RecyclerView focusList = binding.FocusList;
        focusList.setLayoutManager(new LinearLayoutManager(this));
        focusList.setAdapter(focusAdapter);
        focusList.setNestedScrollingEnabled(false);
        RecyclerView laterFocusList = binding.FocusLaterList;
        laterFocusList.setLayoutManager(new LinearLayoutManager(this));
        laterFocusList.setAdapter(laterFocusAdapter);
        laterFocusList.setNestedScrollingEnabled(false);

        obligationAdapter = new ObligationAdapter(new ObligationAdapter.Listener() {
            @Override public void onComplete(String id) { complete(id); }
            @Override public void onMove(String id, MoveWorkItemUseCase.Direction direction) {
                viewModel.move(id, direction);
                Snackbar.make(binding.Root, "Reihenfolge geändert", Snackbar.LENGTH_LONG)
                        .setAction("zurücknehmen", view -> viewModel.undo()).show();
            }
            @Override public void onEdit(String id, boolean routine) {
                viewModel.openEditor(routine, id);
            }
            @Override public void onOmit(String id) { omitToday(id); }
            @Override public void onDelete(WorkItemRow item) { confirmDelete(item); }
        });
        RecyclerView obligations = binding.ObligationList;
        obligations.setLayoutManager(new LinearLayoutManager(this));
        obligations.setAdapter(obligationAdapter);
        obligations.setNestedScrollingEnabled(false);

        binding.AddFab.setOnClickListener(view -> showAddMenu());
        binding.AiBulkEdit.setOnClickListener(view -> ensureAiReady(this::showAiInstruction));
        binding.AiProgressCancel.setOnClickListener(view -> aiViewModel.cancel());
        binding.PlanningSettings.setOnClickListener(
                view -> viewModel.openPlanningSettings());
        updateAction.setOnClickListener(view -> handleUpdateAction());
        binding.NavToday.setOnClickListener(view -> navigateTo("today"));
        binding.NavAll.setOnClickListener(view -> navigateTo("all"));
        binding.NavAi.setOnClickListener(view -> navigateTo("ai"));
        binding.FilterOpen.setOnClickListener(view -> viewModel.selectFilter("open"));
        binding.FilterRoutines.setOnClickListener(view -> viewModel.selectFilter("routines"));
        binding.FilterDone.setOnClickListener(view -> viewModel.selectFilter("done"));
        binding.CompletedCleanupAction.setOnClickListener(view -> confirmCompletedCleanup());
        binding.ThemeMode.setOnClickListener(view -> daylightController.cycleMode());
        undoAction.setOnClickListener(view -> viewModel.undo());
        binding.TodayUndoAction.setOnClickListener(view -> viewModel.undo());
        binding.CalendarPermissionAction.setOnClickListener(view -> requestCalendarAccess());
        binding.CalendarPermissionSkip.setOnClickListener(view -> {
            calendarCardDismissed = true;
            calendarPermissionCard.setVisibility(View.GONE);
        });
        updateCalendarPermissionCard();
        viewModel.state().observe(this, this::render);
        updateViewModel.state().observe(this, this::renderUpdate);
        aiViewModel.state().observe(this, this::renderAi);
        if (!aiViewModel.state().getValue().modelReady() && aiViewModel.termsAccepted()) {
            aiViewModel.installBundledModel(false);
        }
        if (BuildConfig.DEBUG) {
            updateStatus.setText("Lokaler Debug-Build · Updates sind deaktiviert");
            updateAction.setVisibility(View.GONE);
            binding.UpdateProgress.setVisibility(View.GONE);
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
        if (calendarObserver != null) calendarObserver.start();
        if (daylightController != null) daylightController.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            updateCalendarPermissionCard();
            reload();
        }
    }

    @Override
    protected void onStop() {
        if (calendarObserver != null) calendarObserver.stop();
        if (daylightController != null) daylightController.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (calendarObserver != null) calendarObserver.close();
        calendarObserver = null;
        super.onDestroy();
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
            refreshEveningPalette();
            submitTodayRows();
            int additional = Math.max(0, dashboard.focus().size() - 3);
            binding.FocusStack.setVisibility(additional == 0 ? View.GONE : View.VISIBLE);
            ViewGroup.MarginLayoutParams focusMargins =
                    (ViewGroup.MarginLayoutParams) binding.FocusList.getLayoutParams();
            int wantedTopMargin = additional == 0 ? 0 : -dp(92);
            if (focusMargins.topMargin != wantedTopMargin) {
                focusMargins.topMargin = wantedTopMargin;
                binding.FocusList.setLayoutParams(focusMargins);
            }
            binding.FocusMore.setVisibility(additional == 0 ? View.GONE : View.VISIBLE);
            binding.FocusMore.setText(additional == 1
                    ? "und ein weiteres Blatt heute · zeigen"
                    : "und " + additional + " weitere heute · zeigen");
            binding.FocusMore.setOnClickListener(view -> viewModel.selectSurface("all"));
            submitFilteredObligations();
            renderEmptyFocus(state.dashboard());
            renderCalendar(dashboard.calendar());
            allHeading.setText("Alles · " + dashboard.workItems().size());
            String undoLabel = state.dashboard().undoLabel();
            undoAction.setVisibility(undoLabel == null ? View.GONE : View.VISIBLE);
            undoAction.setContentDescription(undoLabel);
            binding.TodayUndoRow.setVisibility(undoLabel != null
                    && undoLabel.startsWith("Aus heute genommen") ? View.VISIBLE : View.GONE);
            renderPlanningConflicts(state.dashboard().conflicts());
            renderEditor(state);
        }
        renderPlanningSettings(state);
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
    public MainViewModel mainViewModel() { return viewModel; }

    @Override
    public AiViewModel aiViewModel() { return aiViewModel; }

    private void renderPlanningConflicts(List<PlanConflict> conflicts) {
        if (conflicts.isEmpty()) {
            planningConflicts.setVisibility(View.GONE);
            return;
        }
        StringBuilder message = new StringBuilder("heute kein passendes Zeitfenster");
        conflicts.stream().limit(3).forEach(conflict -> message.append("\n• ")
                .append(conflict.workItem().title()).append(" — ")
                .append(switch (conflict.reason()) {
                    case AFTER_DEADLINE -> "passt vorher in kein freies Fenster";
                    case NO_CAPACITY -> "sobald wieder genug Platz ist";
                    case OUTSIDE_HORIZON -> "wird später vorgeschlagen";
                }));
        if (conflicts.size() > 3) message.append("\n• und ").append(conflicts.size() - 3)
                .append(" weitere");
        planningConflicts.setText(message.toString());
        planningConflicts.setVisibility(View.VISIBLE);
    }

    private void renderEmptyFocus(com.autosecretary.application.DashboardData data) {
        boolean empty = dashboard.focus().isEmpty();
        emptyFocus.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.CompletedToday.removeAllViews();
        binding.CompletedToday.setVisibility(View.GONE);
        if (!empty) return;

        LocalDate today = LocalDate.now();
        List<String> completedIds = data.completions().stream()
                .filter(value -> value.completedAt().toLocalDate().equals(today))
                .map(com.autosecretary.application.CompletionRecord::workItemId)
                .distinct().collect(java.util.stream.Collectors.toList());
        List<com.autosecretary.domain.WorkItem> completedItems = data.workItems().stream()
                .filter(item -> completedIds.contains(item.id()))
                .collect(java.util.stream.Collectors.toList());
        if (!completedItems.isEmpty()) {
            int minutes = completedItems.stream().mapToInt(
                    com.autosecretary.domain.WorkItem::durationMinutes).sum();
            int weeks = completedItems.stream().mapToInt(
                    item -> item.stats().currentStreak()).max().orElse(1);
            binding.EmptyFocusMarker.setText("geschafft");
            binding.EmptyFocusTitle.setText("Heute ist alles erledigt.");
            binding.EmptyFocusDetail.setText(completedItems.size() == 1
                    ? "ein Blatt, ca. " + minutes + " Min"
                    : completedItems.size() + " Blätter, ca. " + minutes + " Min");
            binding.EmptyAnnualRing.setText(Integer.toString(Math.max(1, weeks)));
            binding.EmptyAnnualRing.setVisibility(View.VISIBLE);
            renderCompletedToday(completedItems);
            return;
        }
        binding.EmptyAnnualRing.setVisibility(View.GONE);
        if (!data.conflicts().isEmpty()) {
            binding.EmptyFocusMarker.setText("heute");
            binding.EmptyFocusTitle.setText("Heute passt kein Blatt ins Zeitfenster.");
            binding.EmptyFocusDetail.setText("Die offenen Aufgaben bleiben unter alles ansehen.");
        } else {
            binding.EmptyFocusMarker.setText("heute");
            binding.EmptyFocusTitle.setText(R.string.empty_focus);
            binding.EmptyFocusDetail.setText("Mit ＋ wächst eine neue Aufgabe.");
        }
    }

    private void submitTodayRows() {
        List<FocusRow> visible = dashboard.focus().stream().limit(3)
                .collect(java.util.stream.Collectors.toList());
        java.time.LocalDateTime nextCalendar = dashboard.calendar().stream()
                .filter(value -> value.end().isAfter(java.time.LocalDateTime.now()))
                .map(CalendarRow::start).min(java.time.LocalDateTime::compareTo).orElse(null);
        int split = visible.size();
        if (nextCalendar != null && visible.size() > 1) {
            split = 1;
            while (split < visible.size()
                    && visible.get(split).suggestedStart().isBefore(nextCalendar)) split++;
        }
        focusAdapter.submit(visible.subList(0, split), 0, visible.size());
        laterFocusAdapter.submit(visible.subList(split, visible.size()), split, visible.size());
        binding.FocusLaterList.setVisibility(split == visible.size() ? View.GONE : View.VISIBLE);
    }

    private void renderCompletedToday(List<com.autosecretary.domain.WorkItem> completedItems) {
        for (int index = 0; index < Math.min(2, completedItems.size()); index++) {
            com.autosecretary.domain.WorkItem item = completedItems.get(index);
            String text = "heute erledigt\n" + item.title();
            android.text.SpannableString styled = new android.text.SpannableString(text);
            int split = text.indexOf('\n') + 1;
            styled.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                    0, split - 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new android.text.style.AbsoluteSizeSpan(16, true),
                    0, split - 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new android.text.style.ForegroundColorSpan(
                            ContextCompat.getColor(this, R.color.marker)),
                    0, split - 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new android.text.style.StrikethroughSpan(),
                    split, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            TextView leaf = new TextView(this);
            leaf.setText(styled);
            leaf.setTextSize(21);
            leaf.setTextColor(ContextCompat.getColor(this, R.color.ink_secondary));
            leaf.setTypeface(ResourcesCompat.getFont(this, R.font.newsreader));
            boolean evening = binding.DaylightBackdrop.usesEveningPalette();
            leaf.setBackgroundResource(index % 2 == 0
                    ? evening ? R.drawable.bg_leaf_middle_evening : R.drawable.bg_leaf_middle
                    : evening ? R.drawable.bg_leaf_low_evening : R.drawable.bg_leaf_low);
            leaf.setRotation(index % 2 == 0 ? 1.1f : -1.0f);
            leaf.setPadding(dp(22), dp(15), dp(22), dp(15));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(11);
            binding.CompletedToday.addView(leaf, params);
        }
        binding.CompletedToday.setVisibility(View.VISIBLE);
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
        binding.UpdateProgress.setVisibility(state.busy() ? View.VISIBLE : View.GONE);
        if (state.busy()) {
            updateStatus.setText(state.available() == null
                    ? "Suche nach veröffentlichtem Update …" : "Update wird geladen und geprüft …");
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
            updateStatus.setText("Signierte Updates, Testversionen eingeschlossen");
            updateAction.setText("Nach Updates suchen");
        }
    }

    private void renderAi(AiUiState state) {
        if (state == null) return;
        binding.AiProgress.setVisibility(state.busy() ? View.VISIBLE : View.GONE);
        binding.AiAnnualRings.setRunning(state.busy());
        if (state.busy()) {
            binding.AiProgressText.setText(switch (state.operation()) {
                case INSTALL -> "Das mitgelieferte Modell wird lokal geprüft.";
                case INFERENCE -> "liest die Einträge und den Kalender …";
                case NONE -> "bereitet die lokale KI vor …";
            });
        }
        binding.AiProgressCancel.setVisibility(state.busy()
                && state.operation() == AiUiState.Operation.INSTALL ? View.VISIBLE : View.GONE);
        if (state.busy()) {
            modelStatus.setText(switch (state.operation()) {
                case INSTALL -> R.string.model_importing;
                case INFERENCE -> R.string.ai_working;
                case NONE -> R.string.model_importing;
            });
        } else {
            modelStatus.setText(state.modelReady() ? R.string.model_ready : R.string.model_bundled);
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
        currentSurface = surface;
        boolean today = "today".equals(surface);
        boolean all = "all".equals(surface);
        todayPanel.setVisibility(today ? View.VISIBLE : View.GONE);
        allPanel.setVisibility(all ? View.VISIBLE : View.GONE);
        aiPanel.setVisibility("ai".equals(surface) ? View.VISIBLE : View.GONE);
        setNavState(binding.NavToday, today);
        setNavState(binding.NavAll, all);
        setNavState(binding.NavAi, "ai".equals(surface));
    }

    private void navigateTo(String target) {
        if (target.equals(currentSurface)) return;
        if ("today".equals(currentSurface) && !"today".equals(target)) {
            animateLeavesOut(() -> viewModel.selectSurface(target));
        } else {
            viewModel.selectSurface(target);
        }
    }

    private void animateLeavesOut(Runnable after) {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            after.run();
            return;
        }
        List<View> leaves = new ArrayList<>();
        if ("all".equals(currentSurface)) {
            appendChildren(leaves, binding.ObligationList, 5);
        } else {
            appendChildren(leaves, binding.FocusList, 5);
            appendChildren(leaves, binding.CalendarContext, 5);
            appendChildren(leaves, binding.FocusLaterList, 5);
        }
        int count = leaves.size();
        if (count == 0) {
            after.run();
            return;
        }
        for (int index = 0; index < count; index++) {
            View leaf = leaves.get(index);
            leaf.animate().translationX(dp(72 + index * 7))
                    .translationY(dp(110 + index * 15))
                    .alpha(.18f).setStartDelay(index * 38L).setDuration(420L)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .setListener(index == count - 1
                            ? new android.animation.AnimatorListenerAdapter() {
                                @Override public void onAnimationEnd(android.animation.Animator animation) {
                                    for (View moved : leaves) {
                                        moved.animate().setListener(null);
                                        moved.setTranslationX(0);
                                        moved.setTranslationY(0);
                                        moved.setAlpha(1f);
                                    }
                                    after.run();
                                }
                            } : null).start();
        }
    }

    private static void appendChildren(List<View> result, ViewGroup parent, int maximum) {
        for (int index = 0; index < parent.getChildCount() && result.size() < maximum; index++) {
            result.add(parent.getChildAt(index));
        }
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
                .sorted(java.util.Comparator.comparingInt(item -> groupPriority(item.group())))
                .collect(java.util.stream.Collectors.toList());
        obligationAdapter.submit(filtered);
        boolean completedFilter = "done".equals(listFilter);
        List<String> cleanupIds = completedCleanupIds();
        binding.CompletedCleanup.setVisibility(completedFilter ? View.VISIBLE : View.GONE);
        binding.CompletedCleanupAction.setEnabled(!cleanupIds.isEmpty());
        binding.CompletedCleanupAction.setAlpha(cleanupIds.isEmpty() ? .45f : 1f);
        if (allHeading != null) {
            String title = "Alles · " + filtered.size();
            android.text.SpannableString styled = new android.text.SpannableString(title);
            styled.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                    6, title.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            allHeading.setText(styled);
        }
    }

    private static int groupPriority(String group) {
        return switch (group) {
            case "überfällig" -> 0;
            case "heute", "heute fällig", "heute erledigt" -> 1;
            case "diese Woche", "gestern" -> 2;
            case "ohne Termin", "seltener", "älter" -> 3;
            default -> 4;
        };
    }

    private List<String> completedCleanupIds() {
        LocalDate cutoff = LocalDate.now().minusDays(30);
        return dashboard.workItems().stream()
                .filter(WorkItemRow::completed)
                .filter(item -> item.completedAt() != null && item.completedAt().isBefore(cutoff))
                .map(WorkItemRow::id)
                .collect(java.util.stream.Collectors.toList());
    }

    private void confirmCompletedCleanup() {
        List<String> ids = completedCleanupIds();
        if (ids.isEmpty()) return;
        String count = ids.size() == 1 ? "ein erledigtes Blatt"
                : ids.size() + " erledigte Blätter";
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Erledigtes aufräumen")
                .setMessage(count + " ist älter als 30 Tage. Wirklich löschen?")
                .setPositiveButton("Löschen", (ignored, which) -> viewModel.deleteAll(ids))
                .setNegativeButton("behalten", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.danger));
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
            empty.setTypeface(ResourcesCompat.getFont(
                    this, R.font.newsreader_light_italic));
            empty.setPadding(dp(6), dp(10), dp(6), dp(12));
            calendarContext.addView(empty);
            return;
        }
        DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm");
        for (CalendarRow block : blocks) {
            RowCalendarBinding row = RowCalendarBinding.inflate(
                    getLayoutInflater(), calendarContext, false);
            boolean evening = binding.DaylightBackdrop.usesEveningPalette();
            row.getRoot().setBackgroundResource(evening
                    ? R.drawable.bg_calendar_leaf_evening : R.drawable.bg_calendar_leaf);
            row.CalendarTime.setText(block.start().format(time));
            row.CalendarTitle.setText(block.title());
            if (evening) {
                row.CalendarTime.setTextColor(android.graphics.Color.rgb(147, 195, 210));
                row.CalendarTitle.setTextColor(android.graphics.Color.rgb(147, 195, 210));
                row.CalendarLabel.setTextColor(android.graphics.Color.rgb(112, 153, 168));
            }
            boolean allDay = java.time.Duration.between(block.start(), block.end()).toHours() >= 23;
            row.CalendarTime.setVisibility(allDay ? View.GONE : View.VISIBLE);
            row.CalendarLabel.setText(allDay ? "ganztägig gesperrt"
                    : "Kalendertermin".equals(block.title())
                    ? "privat · Titel nicht lesbar" : "im Kalender, fest");
            row.getRoot().setRotation(calendarContext.getChildCount() % 2 == 0 ? -0.8f : 0.8f);
            calendarContext.addView(row.getRoot());
        }
    }

    private void refreshEveningPalette() {
        if (binding == null || binding.DaylightBackdrop == null) return;
        boolean evening = binding.DaylightBackdrop.usesEveningPalette();
        if (focusAdapter != null) focusAdapter.setEvening(evening);
        if (laterFocusAdapter != null) laterFocusAdapter.setEvening(evening);
        if (obligationAdapter != null) obligationAdapter.setEvening(evening);
        binding.FocusStack.getChildAt(0).setBackgroundResource(evening
                ? R.drawable.bg_leaf_low_evening : R.drawable.bg_leaf_low);
        binding.FocusStack.getChildAt(1).setBackgroundResource(evening
                ? R.drawable.bg_leaf_middle_mirror_evening : R.drawable.bg_leaf_middle_mirror);
        binding.EmptyFocus.setBackgroundResource(evening
                ? R.drawable.bg_leaf_focus_evening : R.drawable.bg_leaf_focus);
        binding.Greeting.setTextColor(evening ? 0xFFBCAB8C
                : ContextCompat.getColor(this, R.color.marker));
        binding.FocusMore.setTextColor(evening ? 0xFFA08B62
                : ContextCompat.getColor(this, R.color.marker));
        if (dashboard != null && calendarContext != null) renderCalendar(dashboard.calendar());
    }

    private void updateCalendarPermissionCard() {
        if (calendarPermissionCard == null) return;
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
        calendarPermissionCard.setVisibility(granted || calendarCardDismissed
                ? View.GONE : View.VISIBLE);
        if (!granted) {
            boolean asked = getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
                    .getBoolean(CALENDAR_ASKED, false);
            boolean denied = asked
                    && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR);
            binding.CalendarPermissionTitle.setText(denied
                    ? "Ohne Kalender plant die App blind." : "Kalender als Umgebung");
            binding.CalendarPermissionBody.setText(denied
                    ? "Die App fragt nicht erneut. Du kannst den Zugriff in den Einstellungen erlauben."
                    : "Termine bleiben unverändert. Sie helfen nur dabei, echte freie Zeit zu finden.");
            binding.CalendarPermissionSkip.setVisibility(denied ? View.GONE : View.VISIBLE);
            binding.CalendarPermissionAction.setText(
                    denied
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

    private void omitToday(String id) {
        if (!"today".equals(currentSurface)
                || !android.animation.ValueAnimator.areAnimatorsEnabled()) {
            viewModel.omitToday(id);
            return;
        }
        animateTodayOmission(id, () -> viewModel.omitToday(id));
    }

    private void animateTodayOmission(String id, Runnable after) {
        List<View> leaves = new ArrayList<>();
        View target = findTaggedChild(binding.FocusList, id);
        if (target == null) target = findTaggedChild(binding.FocusLaterList, id);
        if (target != null) leaves.add(target);
        appendDistinctChildren(leaves, binding.FocusList, 5);
        appendDistinctChildren(leaves, binding.CalendarContext, 5);
        appendDistinctChildren(leaves, binding.FocusLaterList, 5);
        if (binding.FocusStack.getVisibility() == View.VISIBLE) {
            appendDistinctChildren(leaves, binding.FocusStack, 5);
        }
        if (leaves.isEmpty()) {
            after.run();
            return;
        }
        for (int index = 0; index < leaves.size(); index++) {
            View leaf = leaves.get(index);
            leaf.animate().translationX(dp(74 + index * 8))
                    .translationY(dp(112 + index * 14)).alpha(.12f)
                    .setStartDelay(index * 34L).setDuration(420L)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .setListener(index == leaves.size() - 1
                            ? new android.animation.AnimatorListenerAdapter() {
                                @Override public void onAnimationEnd(android.animation.Animator animation) {
                                    for (View moved : leaves) {
                                        moved.animate().setListener(null);
                                        moved.setTranslationX(0);
                                        moved.setTranslationY(0);
                                        moved.setAlpha(1f);
                                    }
                                    after.run();
                                }
                            } : null).start();
        }
    }

    private static View findTaggedChild(ViewGroup parent, String tag) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            if (tag.equals(child.getTag())) return child;
        }
        return null;
    }

    private static void appendDistinctChildren(
            List<View> result, ViewGroup parent, int maximum) {
        for (int index = 0; index < parent.getChildCount() && result.size() < maximum; index++) {
            View child = parent.getChildAt(index);
            if (!result.contains(child)) result.add(child);
        }
    }

    private void confirmDelete(WorkItemRow item) {
        String detail = item.routine()
                ? "„" + item.title() + "“ mit seinen Schritten und dem Jahresring löschen?"
                : "„" + item.title() + "“ wirklich löschen?";
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Eintrag löschen")
                .setMessage(detail)
                .setPositiveButton("Löschen", (ignored, which) -> viewModel.delete(item.id()))
                .setNegativeButton("behalten", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.danger));
    }

    private void showAiInstruction() {
        if (getSupportFragmentManager().findFragmentByTag(AiInstructionDialogFragment.TAG) == null
                && !getSupportFragmentManager().isStateSaved()) {
            new AiInstructionDialogFragment().show(
                    getSupportFragmentManager(), AiInstructionDialogFragment.TAG);
        }
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
}
