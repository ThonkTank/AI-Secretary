package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.TaskStepUiModel;

import android.Manifest;
import android.animation.LayoutTransition;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;
import de.thonktank.autosecretary.update.presentation.UpdateUiController;
import de.thonktank.autosecretary.update.presentation.UpdateViewModel;

/** Lifecycle host for the state-driven dashboard view hierarchy. */
public class MainActivity extends ComponentActivity {
    public static final String CONFIRM_TASK = "confirm_task";
    public static final String CONFIRM_TASK_TITLE = "confirm_task_title";
    public static final String OPEN_EDITOR = "open_editor";
    private final Handler minuteHandler = new Handler(Looper.getMainLooper());
    private AppContainer container;
    private TaskViewModel viewModel;
    private UpdateUiController updates;
    private DashboardUiState uiState;
    private UpdateUiState updateState = UpdateUiState.idle();
    private ForestBackdropView forest;
    private HeaderView header;
    private FooterNavigationView footer;
    private ScrollView scroll;
    private DashboardRenderer renderer;
    private FrameLayout root;
    private LinearLayout dashboardScreen;
    private TaskEditorCoordinator editorCoordinator;
    private int systemTopInset;
    private final RewardAnchorRegistry rewardAnchors = new RewardAnchorRegistry();
    private RewardAnimator rewardAnimator;

    private final ActivityResultLauncher<String> calendarPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                syncCalendarPermission();
                viewModel.load();
            });

    private final ActivityResultLauncher<Intent> installPermission = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (updates != null) updates.onInstallPermissionResult();
            });

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        container = AutoSecretaryApplication.from(this).container();
        EdgeToEdge.enable(this);
        buildShell();
        viewModel = new ViewModelProvider(this,
                new TaskViewModel.Factory(container)).get(TaskViewModel.class);
        editorCoordinator = new TaskEditorCoordinator(this, root, dashboardScreen,
                new TaskEditorView.Listener() {
                    @Override public void onDraftChanged(EditorUiState draft) {
                        viewModel.updateEditorDraft(draft);
                    }
                    @Override public void onSave(EditorUiState draft) {
                        viewModel.saveEditor(draft);
                    }
                    @Override public void onDelete(String taskId) {
                        viewModel.deleteFromEditor(taskId);
                    }
                    @Override public void onDismiss() { viewModel.dismissEditor(); }
                });
        UpdateViewModel updateViewModel = new ViewModelProvider(this, new UpdateViewModel.Factory(
                container.updates, container.updatePreferences,
                failure -> container.logger.error("Updater", failure.getMessage(), failure),
                container.texts, container.updateClock,
                container.updateExecutors)).get(UpdateViewModel.class);
        updates = new UpdateUiController(updateViewModel, new AndroidUpdateDialogs(this),
                new AndroidUpdatePlatform(this, installPermission, container.updateInstaller,
                        container.logger, container.updateConfiguration.repositoryOwner,
                        container.updateConfiguration.repositoryName),
                container.texts, container.updateConfiguration.automaticChecksEnabled);
        viewModel.state().observe(this, this::render);
        viewModel.events().observe(this, this::handleEvent);
        viewModel.rewardEffects().observe(this, this::handleRewardEffects);
        updates.state().observe(this, this::renderUpdate);
        updates.effects().observe(this, updates::handleEffect);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (editorCoordinator != null && editorCoordinator.handleBack()) return;
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
        showLegacyResetNotice();
        handleLaunchIntent();
        syncCalendarPermission();
        minuteHandler.post(minuteTick);
    }

    @Override protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            syncCalendarPermission();
            viewModel.load();
        }
        if (updates != null) updates.onResume();
    }

    @Override protected void onDestroy() {
        minuteHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private final Runnable minuteTick = new Runnable() {
        @Override public void run() {
            if (viewModel != null) viewModel.minuteChanged();
            minuteHandler.postDelayed(this, 60_000L);
        }
    };

    private void buildShell() {
        UiStyle style = new UiStyle(this);
        root = new FrameLayout(this);
        forest = new ForestBackdropView(this);
        root.addView(forest, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout screen = new LinearLayout(this);
        dashboardScreen = screen;
        screen.setId(R.id.dashboard_screen);
        screen.setOrientation(LinearLayout.VERTICAL);
        root.addView(screen, new FrameLayout.LayoutParams(-1, -1));
        header = new HeaderView(this, this::openEditorWithFlight);
        rewardAnchors.register(RewardAnchorKey.head(), header.rewardAnchor());
        screen.addView(header, new LinearLayout.LayoutParams(-1,
                getResources().getDimensionPixelSize(R.dimen.header_height)));
        scroll = new ScrollView(this);
        scroll.setId(R.id.dashboard_scroll);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setId(R.id.dashboard_content);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutTransition(new LayoutTransition());
        content.getLayoutTransition().setDuration(MotionTokens.standard().stateChangeDurationMs);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        footer = new FooterNavigationView(this, destination -> viewModel.navigate(destination));
        screen.addView(footer, new LinearLayout.LayoutParams(-1,
                getResources().getDimensionPixelSize(R.dimen.footer_height)));
        renderer = new DashboardRenderer(this, scroll, content, dashboardActions(), versionName(),
                rewardAnchors);
        rewardAnimator = new RewardAnimator(root, header, rewardAnchors);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            systemTopInset = bars.top;
            screen.setPadding(0, bars.top, 0, 0);
            if (editorCoordinator != null) editorCoordinator.setInsets(bars.top, bars.bottom);
            return insets;
        });
        setContentView(root);
        ViewCompat.requestApplyInsets(root);
        DayPalette initial = DayPalette.at(container.clock.time(), DayPalette.Mode.AUTO);
        forest.setPalette(initial);
        header.bind(container.clock.time(), initial, TodayUiModel.empty().xpProgress);
        footer.bind(NavigationDestination.TODAY, initial);
    }

    private DashboardRenderer.Actions dashboardActions() {
        return new DashboardRenderer.Actions() {
            @Override public void onAddTask() { openEditorWithFlight(); }
            @Override public void onTaskAction(TaskSnapshot task) {
                if (task.undoAvailable) viewModel.undoOccurrence(task.occurrenceId);
                else completeOrConfirm(task);
            }
            @Override public void onTaskMenu(TaskSnapshot task) { showTaskMenu(task); }
            @Override public void onComplete(TaskSnapshot task) { completeOrConfirm(task); }
            @Override public void onCompleteRemaining(TaskSnapshot task) {
                viewModel.completeRemaining(task.occurrenceId);
            }
            @Override public void onHarvest(TaskSnapshot task) {
                viewModel.harvest(task.occurrenceId);
            }
            @Override public void onDefer(TaskSnapshot task) {
                viewModel.defer(task.occurrenceId.isEmpty() ? task.taskId : task.occurrenceId);
            }
            @Override public void onToggleStep(TaskStepUiModel step) {
                viewModel.toggleStep(step.id);
            }
            @Override public void onEditStepProgress(TaskStepUiModel step,
                                                     java.util.List<Integer> repetitions,
                                                     boolean done) {
                viewModel.editStepProgress(step.id, repetitions);
            }
            @Override public void onFinishExercise(TaskStepUiModel step) {
                viewModel.finishExercise(step.id);
            }
            @Override public void onReopenExercise(TaskStepUiModel step,
                                                    java.util.List<Integer> repetitions) {
                viewModel.reopenExercise(step.id, repetitions);
            }
            @Override public void onSetProgressEditorStateChanged(
                    SetProgressEditorState state) {
                viewModel.updateSetProgressEditor(state);
            }
            @Override public void onTheme(UiThemeMode mode) {
                container.uiPreferences.setThemeMode(mode);
                viewModel.minuteChanged();
                TaskWidgetProvider.updateAll(MainActivity.this);
            }
            @Override public void onCalendarPermission() { viewModel.onCalendarPermissionAction(); }
            @Override public void onUpdates() { updates.onManualAction(); }
        };
    }

    private void render(DashboardUiState state) {
        uiState = state;
        forest.setPalette(state.palette);
        header.bind(container.clock.time(), state.palette, state.dashboard.xpProgress);
        footer.bind(state.navigation, state.palette);
        renderer.render(state, container.uiPreferences.themeMode(), updateState);
        boolean light = luminance(state.palette.background) > .55;
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(),
                getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
        editorCoordinator.render(state.editor, state.palette, container.clock.today());
        TaskWidgetProvider.updateAll(this);
    }

    private void openEditorWithFlight() {
        if (viewModel == null) return;
        if (renderer == null || uiState == null
                || uiState.navigation != NavigationDestination.TODAY) {
            viewModel.openEditor(null);
            return;
        }
        renderer.animateEditorTransition(() -> viewModel.openEditor(null));
    }

    private void renderUpdate(UpdateUiState state) {
        updateState = state == null ? UpdateUiState.idle() : state;
        if (uiState != null)
            renderer.render(uiState, container.uiPreferences.themeMode(), updateState);
    }

    private void completeOrConfirm(TaskSnapshot task) {
        if (task.terminalCondition)
            viewModel.requestClose(task.taskId, task.title);
        else viewModel.complete(task.occurrenceId);
    }

    private void showTaskMenu(TaskSnapshot task) {
        new AlertDialog.Builder(this).setTitle(task.title)
                .setItems(new String[]{getString(R.string.task_edit), getString(R.string.task_move),
                        getString(R.string.task_delete)}, (dialog, which) -> {
                    if (which == 0) viewModel.openEditor(task.taskId);
                    else if (which == 1) showMoveDialog(task);
                    else viewModel.requestDelete(task);
                }).show();
    }

    private void showMoveDialog(TaskSnapshot task) {
        TaskSlot[] slots = TaskSlot.values();
        new AlertDialog.Builder(this).setTitle(R.string.task_move)
                .setSingleChoiceItems(slotLabels(), task.slot.ordinal(), (dialog, which) -> {
                    viewModel.move(task.taskId, slots[which]);
                    dialog.dismiss();
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void confirmDelete(TaskSnapshot task) {
        String loss = task.routine()
                ? getString(R.string.delete_routine_loss)
                : getString(R.string.delete_task_loss);
        new AlertDialog.Builder(this).setTitle(getString(R.string.delete_task_title, task.title))
                .setMessage(loss).setNegativeButton(R.string.keep, null)
                .setPositiveButton(R.string.delete,
                        (dialog, which) -> viewModel.delete(task.taskId)).show();
    }

    private void confirmClose(String taskId, String title) {
        new AlertDialog.Builder(this).setTitle(R.string.close_task_title)
                .setMessage(getString(R.string.close_task_message, title))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.condition_met,
                        (dialog, which) -> viewModel.close(taskId)).show();
    }

    private void handleEvent(UiEvent event) {
        if (event == null || !event.consume()) return;
        if (event.type == UiEvent.Type.ERROR)
            new AlertDialog.Builder(this).setTitle(R.string.error_title).setMessage(event.message)
                    .setPositiveButton(R.string.okay, null).show();
        else if (event.type == UiEvent.Type.CONFIRM_DELETE) {
            TaskSnapshot task = findTask(event.taskId);
            if (task != null) confirmDelete(task);
        } else if (event.type == UiEvent.Type.CONFIRM_CLOSE)
            confirmClose(event.taskId, event.taskTitle);
        else if (event.type == UiEvent.Type.REQUEST_CALENDAR_PERMISSION)
            calendarPermission.launch(Manifest.permission.READ_CALENDAR);
        else if (event.type == UiEvent.Type.OPEN_APP_SETTINGS)
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
    }

    private void handleRewardEffects(RewardEffectQueue.Snapshot snapshot) {
        if (snapshot == null || uiState == null || rewardAnimator == null) return;
        RewardEffect effect = snapshot.first();
        if (effect != null) rewardAnimator.play(effect, uiState.palette, systemTopInset,
                () -> viewModel.acknowledgeRewardEffect(effect.id));
    }

    private void syncCalendarPermission() {
        if (viewModel == null) return;
        boolean granted = checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
        viewModel.updateCalendarPermission(granted,
                shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR));
    }

    private void showLegacyResetNotice() {
        if (!AutoSecretaryApplication.from(this).legacyStateCleaner().shouldShowResetNotice()) return;
        new AlertDialog.Builder(this).setTitle(R.string.legacy_reset_title)
                .setMessage(R.string.legacy_reset_message)
                .setPositiveButton(R.string.understood, (dialog, which) ->
                        AutoSecretaryApplication.from(this).legacyStateCleaner()
                                .acknowledgeResetNotice()).show();
    }

    private void handleLaunchIntent() {
        String confirmTask = getIntent().getStringExtra(CONFIRM_TASK);
        String confirmTitle = getIntent().getStringExtra(CONFIRM_TASK_TITLE);
        getIntent().removeExtra(CONFIRM_TASK);
        getIntent().removeExtra(CONFIRM_TASK_TITLE);
        if (confirmTask != null)
            viewModel.requestClose(confirmTask,
                    confirmTitle == null ? getString(R.string.this_project) : confirmTitle);
        boolean openEditor = getIntent().getBooleanExtra(OPEN_EDITOR, false);
        getIntent().removeExtra(OPEN_EDITOR);
        if (openEditor) viewModel.openEditor(null);
    }

    private TaskSnapshot findTask(String taskId) {
        if (uiState == null) return null;
        for (TaskSnapshot task : uiState.dashboard.tasks)
            if (task.taskId.equals(taskId)) return task;
        return null;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException error) {
            container.logger.error("MainActivity", "Could not read installed version", error);
            return "0.1.0";
        }
    }

    private String[] slotLabels() {
        return new String[]{getString(R.string.slot_morning), getString(R.string.slot_midday),
                getString(R.string.slot_evening), getString(R.string.slot_later)};
    }

    private static double luminance(int color) {
        return (.2126 * ((color >> 16) & 255) + .7152 * ((color >> 8) & 255)
                + .0722 * (color & 255)) / 255d;
    }
}
