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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.presentation.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.presentation.databinding.ActivityMainBinding;
import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.application.TimeProvider;
import com.autosecretary.platform.CalendarChangeObserver;
import com.autosecretary.ui.editor.ObligationEditorDialogFragment;
import com.autosecretary.ui.settings.PlanningSettingsDialogFragment;
import com.autosecretary.ui.ai.AiPanelController;
import com.autosecretary.ui.ai.AiViewModel;
import com.autosecretary.ui.editor.AddWorkItemDialogFragment;
import com.autosecretary.ui.update.UpdateUiEffect;
import com.autosecretary.ui.update.UpdatePanelController;
import com.autosecretary.ui.update.UpdateViewModel;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/** One screen: focus anchor, complete list, direct editors and confirmed local-AI bulk changes. */
public final class MainActivity extends AppCompatActivity
        implements FeatureViewModelFactoryOwner {
    private MainViewModel viewModel;
    private TimeProvider time;
    private CelebrationView celebration;
    private TextView undoAction;
    private View calendarPermissionCard;
    private DaylightController daylightController;
    private CalendarChangeObserver calendarObserver;
    private NavigationController navigationController;
    private UpdatePanelController updatePanelController;
    private WorkItemsPanelController workItemsPanelController;
    private TodayPanelController todayPanelController;
    private Bundle creationState;
    private View root;
    private ActivityMainBinding binding;
    private ViewModelProvider.Factory featureViewModelFactory;
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
        }
        root = binding.Root;
        AutoSecretaryApplication app = AutoSecretaryApplication.from(this);
        time = app.graph().clock();
        root.setContentDescription("Auto Secretary · Initialisierung");
        initializeCore(app);
    }

    private void initializeCore(AutoSecretaryApplication app) {
        if (viewModel != null) return;
        ViewModelProvider.Factory factory = app.viewModelFactory(this, creationState);
        featureViewModelFactory = factory;
        viewModel = new ViewModelProvider(this, factory)
                .get(MainViewModel.class);
        UpdateViewModel updateViewModel = new ViewModelProvider(this, factory)
                .get(UpdateViewModel.class);
        AiViewModel aiViewModel = new ViewModelProvider(this, factory)
                .get(AiViewModel.class);
        celebration = binding.Celebration;
        undoAction = binding.UndoAction;
        calendarPermissionCard = binding.CalendarPermissionCard;
        daylightController = new DaylightController(this, root,
                binding.DaylightBackdrop, binding.ThemeMode, binding.Greeting,
                app.location(),
                time,
                () -> locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION),
                this::refreshEveningPalette);
        calendarObserver = new CalendarChangeObserver(this, app.executors().main(), () -> {
            reload();
            app.graph().refreshWidgets();
        });
        daylightController.configure();
        navigationController = new NavigationController(binding, viewModel::selectSurface);
        updatePanelController = new UpdatePanelController(binding, updateViewModel,
                !com.autosecretary.BuildConfig.DEBUG,
                () -> getPackageManager().canRequestPackageInstalls(),
                this::handleUpdateEffect);
        AiPanelController aiPanelController = new AiPanelController(
                this, binding, aiViewModel, this::showError);

        todayPanelController = new TodayPanelController(binding, time,
                creationState != null && creationState.getBoolean(TODAY_EXPANDED),
                new TodayPanelController.Actions() {
                    @Override public void complete(String id) { MainActivity.this.complete(id); }
                    @Override public void setStepCompleted(
                            String itemId, String stepId, boolean completed) {
                        MainActivity.this.setStepCompleted(itemId, stepId, completed);
                    }
                    @Override public void move(
                            String id, MoveWorkItemUseCase.Direction direction) {
                        viewModel.move(id, direction);
                    }
                    @Override public void omitToday(String id) { MainActivity.this.omitToday(id); }
                    @Override public void undo() { viewModel.undo(); }
                });

        workItemsPanelController = new WorkItemsPanelController(binding, time,
                new WorkItemsPanelController.Actions() {
                    @Override public void selectFilter(WorkItemFilter filter) {
                        viewModel.selectFilter(filter);
                    }
                    @Override public void complete(String id) { MainActivity.this.complete(id); }
                    @Override public void move(
                            String id, MoveWorkItemUseCase.Direction direction) {
                        viewModel.move(id, direction);
                    }
                    @Override public void edit(String id, boolean routine) {
                        viewModel.openEditor(routine, id);
                    }
                    @Override public void omitToday(String id) { MainActivity.this.omitToday(id); }
                    @Override public void confirmDelete(WorkItemRow item) {
                        MainActivity.this.confirmDelete(item);
                    }
                    @Override public void undo() { viewModel.undo(); }
                    @Override public void deleteAll(List<String> ids) {
                        viewModel.deleteAll(ids);
                    }
                });

        binding.AddFab.setOnClickListener(view -> showAddMenu());
        binding.PlanningSettings.setOnClickListener(
                view -> viewModel.openPlanningSettings());
        binding.ThemeMode.setOnClickListener(view -> daylightController.cycleMode());
        undoAction.setOnClickListener(view -> viewModel.undo());
        binding.CalendarPermissionAction.setOnClickListener(view -> requestCalendarAccess());
        binding.CalendarPermissionSkip.setOnClickListener(view -> {
            calendarCardDismissed = true;
            calendarPermissionCard.setVisibility(View.GONE);
        });
        updateCalendarPermissionCard();
        viewModel.state().observe(this, this::render);
        viewModel.effects().observe(this, this::handleMainEffect);
        updatePanelController.bind(this);
        aiPanelController.bind(this);
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
            updatePanelController.onResume();
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
        navigationController.render(state.surface());
        if (state.dashboard() != null) {
            Dashboard dashboard = UiModelMapper.dashboard(state.dashboard(), time.localNow());
            refreshEveningPalette();
            workItemsPanelController.render(dashboard, state.filter());
            String undoLabel = state.dashboard().undoLabel();
            undoAction.setVisibility(undoLabel == null ? View.GONE : View.VISIBLE);
            undoAction.setContentDescription(undoLabel);
            todayPanelController.render(dashboard, state.dashboard(),
                    state.dashboard().conflicts(), undoLabel);
            renderEditor(state);
        }
        renderPlanningSettings(state);
    }

    private void handleMainEffect(MainUiEffect effect) {
        if (effect == null) return;
        viewModel.consumeEffect(effect.id());
        if (effect instanceof MainUiEffect.Completion) {
            celebration.burst();
            celebration.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                    ? HapticFeedbackConstants.CONFIRM
                    : HapticFeedbackConstants.KEYBOARD_TAP);
            Snackbar.make(binding.Root, "Erledigt", Snackbar.LENGTH_SHORT).show();
        } else if (effect instanceof MainUiEffect.Error error) {
            Toast.makeText(this, error.message(), Toast.LENGTH_LONG).show();
            viewModel.consumeError();
        }
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        outState.putBoolean(TODAY_EXPANDED,
                todayPanelController != null && todayPanelController.expanded());
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

    @Override public ViewModelProvider.Factory featureViewModelFactory() {
        if (featureViewModelFactory == null) {
            featureViewModelFactory = AutoSecretaryApplication.from(this)
                    .viewModelFactory(this, creationState);
        }
        return featureViewModelFactory;
    }

    private void handleUpdateEffect(UpdateUiEffect effect) {
        if (effect == null) return;
        AutoSecretaryApplication app = AutoSecretaryApplication.from(this);
        if (effect instanceof UpdateUiEffect.OpenUnknownSourcesSettings) {
            startActivity(app.updateSettingsIntent(this));
        } else if (effect instanceof UpdateUiEffect.OpenInstaller installer) {
            startActivity(app.updateInstallerIntent(this, installer.update()));
        }
    }

    private void showAddMenu() {
        if (getSupportFragmentManager().findFragmentByTag(AddWorkItemDialogFragment.TAG) == null
                && !getSupportFragmentManager().isStateSaved()) {
            new AddWorkItemDialogFragment().show(
                    getSupportFragmentManager(), AddWorkItemDialogFragment.TAG);
        }
    }

    private void refreshEveningPalette() {
        if (binding == null || binding.DaylightBackdrop == null) return;
        boolean evening = binding.DaylightBackdrop.usesEveningPalette();
        if (todayPanelController != null) todayPanelController.setEvening(evening);
        if (workItemsPanelController != null) workItemsPanelController.setEvening(evening);
        binding.Greeting.setTextColor(evening ? 0xFFBCAB8C
                : ContextCompat.getColor(this, R.color.marker));
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
        MainUiState state = viewModel.state().getValue();
        if (state == null || state.surface() != Surface.TODAY
                || !android.animation.ValueAnimator.areAnimatorsEnabled()) {
            viewModel.omitToday(id);
            return;
        }
        animateTodayOmission(id, () -> viewModel.omitToday(id));
    }

    private void animateTodayOmission(String id, Runnable after) {
        List<View> leaves = new ArrayList<>();
        View target = findTaggedChild(binding.FocusList, id);
        if (target != null) leaves.add(target);
        appendDistinctChildren(leaves, binding.FocusList, 5);
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

    private void showError(String message) {
        if (getSupportFragmentManager().findFragmentByTag(ErrorDialogFragment.TAG) == null
                && !getSupportFragmentManager().isStateSaved()) {
            ErrorDialogFragment.create(message).show(
                    getSupportFragmentManager(), ErrorDialogFragment.TAG);
        }
    }

    private static final String UI_PREFERENCES = "waldmorgen_ui";
    private static final String CALENDAR_ASKED = "calendar_asked";
    private static final String TODAY_EXPANDED = "todayExpanded";
}
