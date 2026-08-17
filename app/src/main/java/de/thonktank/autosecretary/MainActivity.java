package de.thonktank.autosecretary;

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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

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
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;
import de.thonktank.autosecretary.update.presentation.UpdateUiController;
import de.thonktank.autosecretary.update.presentation.UpdateViewModel;

/** Lifecycle host for the state-driven dashboard view hierarchy. */
public class MainActivity extends ComponentActivity {
    public static final String CONFIRM_TASK = "confirm_task";
    public static final String CONFIRM_TASK_TITLE = "confirm_task_title";
    public static final String CONFIRM_TASK_RING_WEEKS = "confirm_task_ring_weeks";
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
    private TaskEditorView taskEditor;
    private SetConfirmationView setConfirmation;
    private int systemTopInset;

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
        updates.state().observe(this, this::renderUpdate);
        updates.effects().observe(this, updates::handleEffect);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (setConfirmation != null && setConfirmation.handleBack()) return;
                if (taskEditor != null && taskEditor.handleBack()) return;
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
        screen.setOrientation(LinearLayout.VERTICAL);
        root.addView(screen, new FrameLayout.LayoutParams(-1, -1));
        header = new HeaderView(this, this::openEditorWithFlight);
        screen.addView(header, new LinearLayout.LayoutParams(-1,
                getResources().getDimensionPixelSize(R.dimen.header_height)));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutTransition(new LayoutTransition());
        content.getLayoutTransition().setDuration(MotionTokens.standard().stateChangeDurationMs);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        footer = new FooterNavigationView(this, destination -> viewModel.navigate(destination));
        screen.addView(footer, new LinearLayout.LayoutParams(-1,
                getResources().getDimensionPixelSize(R.dimen.footer_height)));
        renderer = new DashboardRenderer(this, scroll, content, dashboardActions(), versionName());
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            systemTopInset = bars.top;
            screen.setPadding(0, bars.top, 0, 0);
            if (taskEditor != null) taskEditor.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        setContentView(root);
        ViewCompat.requestApplyInsets(root);
        DayPalette initial = DayPalette.at(container.clock.time(), DayPalette.Mode.AUTO);
        forest.setPalette(initial);
        header.bind(container.clock.time(), initial);
        footer.bind(NavigationDestination.TODAY, initial);
    }

    private DashboardRenderer.Actions dashboardActions() {
        return new DashboardRenderer.Actions() {
            @Override public void onAddTask() { openEditorWithFlight(); }
            @Override public void onTaskAction(TaskSnapshot task) { completeOrConfirm(task); }
            @Override public void onTaskMenu(TaskSnapshot task) { showTaskMenu(task); }
            @Override public void onComplete(TaskSnapshot task) { completeOrConfirm(task); }
            @Override public void onDefer(TaskSnapshot task) {
                viewModel.defer(task.occurrenceId.isEmpty() ? task.taskId : task.occurrenceId);
            }
            @Override public void onToggleStep(TaskStepSnapshot step) {
                if (step.amountKind == StepAmountKind.SETS_REPS && !step.done)
                    showSetConfirmation(step);
                else viewModel.toggleStep(step.id);
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
        header.bind(container.clock.time(), state.palette);
        footer.bind(state.navigation, state.palette);
        renderer.render(state, container.uiPreferences.themeMode(), updateState);
        boolean light = luminance(state.palette.background) > .55;
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(),
                getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
        syncEditor(state.editor);
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
            viewModel.requestClose(task.taskId, task.title, task.ringWeeks);
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

    private void showSetConfirmation(TaskStepSnapshot step) {
        if (setConfirmation != null || taskEditor != null || uiState == null) return;
        setConfirmation = new SetConfirmationView(this, step, uiState.palette,
                new SetConfirmationView.Listener() {
                    @Override public void onConfirm(String stepId, int repetitions) {
                        viewModel.confirmSet(stepId, repetitions); closeSetConfirmation();
                    }
                    @Override public void onFinish(String stepId) {
                        viewModel.finishExercise(stepId); closeSetConfirmation();
                    }
                    @Override public void onDismiss() { closeSetConfirmation(); }
                });
        setConfirmation.setPadding(0, systemTopInset, 0, 0);
        root.addView(setConfirmation, new FrameLayout.LayoutParams(-1, -1));
    }

    private void closeSetConfirmation() {
        if (setConfirmation != null) root.removeView(setConfirmation);
        setConfirmation = null;
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
                ? getResources().getQuantityString(R.plurals.delete_routine_loss,
                        task.ringWeeks, task.ringWeeks)
                : getString(R.string.delete_task_loss);
        new AlertDialog.Builder(this).setTitle(getString(R.string.delete_task_title, task.title))
                .setMessage(loss).setNegativeButton(R.string.keep, null)
                .setPositiveButton(R.string.delete,
                        (dialog, which) -> viewModel.delete(task.taskId)).show();
    }

    private void confirmClose(String taskId, String title, int ringWeeks) {
        String suffix = ringWeeks > 0 ? getResources().getQuantityString(
                R.plurals.close_ring_suffix, ringWeeks, ringWeeks) : "";
        new AlertDialog.Builder(this).setTitle(R.string.close_task_title)
                .setMessage(getString(R.string.close_task_message, title, suffix))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.condition_met,
                        (dialog, which) -> viewModel.close(taskId)).show();
    }

    private void syncEditor(EditorUiState editor) {
        if (!editor.open) {
            if (taskEditor != null) {
                root.removeView(taskEditor);
                taskEditor = null;
            }
            dashboardScreen.setVisibility(android.view.View.VISIBLE);
            return;
        }
        dashboardScreen.setVisibility(android.view.View.INVISIBLE);
        if (editor.loading) return;
        if (taskEditor == null) {
            taskEditor = new TaskEditorView(this, new TaskEditorView.Listener() {
            @Override public void onDraftChanged(EditorUiState draft) { viewModel.updateEditorDraft(draft); }
            @Override public void onSave(EditorUiState draft) { viewModel.saveEditor(draft); }
            @Override public void onDelete(String taskId) { viewModel.deleteFromEditor(taskId); }
            @Override public void onDismiss() { viewModel.dismissEditor(); }
            });
            taskEditor.setPadding(0, systemTopInset, 0, 0);
            root.addView(taskEditor, new FrameLayout.LayoutParams(-1, -1));
        }
        taskEditor.bind(editor, uiState.palette, container.clock.today());
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
            confirmClose(event.taskId, event.taskTitle, event.ringWeeks);
        else if (event.type == UiEvent.Type.REQUEST_CALENDAR_PERMISSION)
            calendarPermission.launch(Manifest.permission.READ_CALENDAR);
        else if (event.type == UiEvent.Type.OPEN_APP_SETTINGS)
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
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
        int confirmRingWeeks = getIntent().getIntExtra(CONFIRM_TASK_RING_WEEKS, 0);
        getIntent().removeExtra(CONFIRM_TASK);
        getIntent().removeExtra(CONFIRM_TASK_TITLE);
        getIntent().removeExtra(CONFIRM_TASK_RING_WEEKS);
        if (confirmTask != null)
            viewModel.requestClose(confirmTask,
                    confirmTitle == null ? getString(R.string.this_project) : confirmTitle,
                    confirmRingWeeks);
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
