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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.update.UpdateInfo;
import de.thonktank.autosecretary.update.UpdateUiState;
import de.thonktank.autosecretary.update.VerifiedUpdate;

import java.util.Locale;

/** Lifecycle host for the state-driven dashboard view hierarchy. */
public class MainActivity extends ComponentActivity {
    public static final String CONFIRM_TASK = "confirm_task";
    public static final String CONFIRM_TASK_TITLE = "confirm_task_title";
    public static final String CONFIRM_TASK_RING_WEEKS = "confirm_task_ring_weeks";
    public static final String OPEN_EDITOR = "open_editor";
    private final Handler minuteHandler = new Handler(Looper.getMainLooper());
    private AppContainer container;
    private TaskViewModel viewModel;
    private UpdateViewModel updateViewModel;
    private DashboardUiState uiState;
    private UpdateUiState updateState = UpdateUiState.idle();
    private ForestBackdropView forest;
    private HeaderView header;
    private FooterNavigationView footer;
    private ScrollView scroll;
    private DashboardRenderer renderer;
    private AlertDialog taskEditorDialog;

    private final ActivityResultLauncher<String> calendarPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                syncCalendarPermission();
                viewModel.load();
            });

    private final ActivityResultLauncher<Intent> installPermission = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (container != null && container.updateInstaller.canInstallPackages(this)
                        && updateViewModel != null) updateViewModel.requestInstall();
            });

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        container = AutoSecretaryApplication.from(this).container();
        EdgeToEdge.enable(this);
        buildShell();
        viewModel = new ViewModelProvider(this,
                new TaskViewModel.Factory(container)).get(TaskViewModel.class);
        updateViewModel = new ViewModelProvider(this,
                new UpdateViewModel.Factory(container)).get(UpdateViewModel.class);
        viewModel.state().observe(this, this::render);
        viewModel.events().observe(this, this::handleEvent);
        updateViewModel.state().observe(this, this::renderUpdate);
        updateViewModel.events().observe(this, this::handleUpdateEvent);
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
        if (!BuildConfig.DEBUG && updateViewModel != null) updateViewModel.automaticCheck();
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
        FrameLayout root = new FrameLayout(this);
        forest = new ForestBackdropView(this);
        root.addView(forest, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        root.addView(screen, new FrameLayout.LayoutParams(-1, -1));
        header = new HeaderView(this, () -> viewModel.openEditor(null));
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
            screen.setPadding(0, bars.top, 0, bars.bottom);
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
            @Override public void onAddTask() { viewModel.openEditor(null); }
            @Override public void onTaskAction(TaskSnapshot task) { completeOrConfirm(task); }
            @Override public void onTaskMenu(TaskSnapshot task) { showTaskMenu(task); }
            @Override public void onComplete(TaskSnapshot task) { completeOrConfirm(task); }
            @Override public void onDefer(TaskSnapshot task) {
                viewModel.defer(task.occurrenceId.isEmpty() ? task.taskId : task.occurrenceId);
            }
            @Override public void onToggleStep(TaskStepSnapshot step) { viewModel.toggleStep(step.id); }
            @Override public void onTheme(UiThemeMode mode) {
                container.uiPreferences.setThemeMode(mode);
                viewModel.minuteChanged();
                TaskWidgetProvider.updateAll(MainActivity.this);
            }
            @Override public void onCalendarPermission() { viewModel.onCalendarPermissionAction(); }
            @Override public void onUpdates() { updateViewModel.manualAction(); }
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
            if (taskEditorDialog != null && taskEditorDialog.isShowing()) taskEditorDialog.dismiss();
            return;
        }
        if (editor.loading || taskEditorDialog != null && taskEditorDialog.isShowing()) return;
        taskEditorDialog = TaskEditorDialog.show(this, editor, new TaskEditorDialog.Listener() {
            @Override public void onDraftChanged(EditorUiState draft) { viewModel.updateEditorDraft(draft); }
            @Override public void onSave(EditorUiState draft) { viewModel.saveEditor(draft); }
            @Override public void onDismiss() {
                taskEditorDialog = null;
                if (!isChangingConfigurations() && !isFinishing()
                        && uiState != null && uiState.editor.open) viewModel.dismissEditor();
            }
        });
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

    private void handleUpdateEvent(UpdateEvent event) {
        if (event == null || !event.consume()) return;
        if (event.type == UpdateEvent.Type.AVAILABLE) showUpdateAvailable(event.update);
        else if (event.type == UpdateEvent.Type.INSTALL) installUpdate(event.verified);
        else showUpdateError(event.message);
    }

    private void showUpdateAvailable(UpdateInfo update) {
        if (update == null || isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.update_available_title, update.versionName))
                .setMessage(getString(R.string.update_available_message,
                        readableSize(update.sizeBytes)))
                .setNegativeButton(R.string.update_later,
                        (dialog, which) -> updateViewModel.postpone(update))
                .setPositiveButton(R.string.update_now,
                        (dialog, which) -> updateViewModel.accept(update)).show();
    }

    private void installUpdate(VerifiedUpdate update) {
        if (update == null || isFinishing()) return;
        if (container.updateInstaller.canInstallPackages(this)) {
            try {
                startActivity(container.updateInstaller.installerIntent(this, update));
            } catch (RuntimeException error) {
                container.logger.error("Updater", "Could not open Android installer", error);
                showUpdateError(getString(R.string.error_update_download));
            }
            return;
        }
        new AlertDialog.Builder(this).setTitle(R.string.unknown_sources_title)
                .setMessage(R.string.unknown_sources_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.open_install_settings, (dialog, which) ->
                        installPermission.launch(container.updateInstaller.settingsIntent(this)))
                .show();
    }

    private void showUpdateError(String message) {
        new AlertDialog.Builder(this).setTitle(R.string.error_title)
                .setMessage(message == null ? getString(R.string.error_update_check) : message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.open_github, (dialog, which) -> startActivity(
                        container.updateInstaller.releasesIntent(BuildConfig.UPDATE_REPOSITORY_OWNER,
                                BuildConfig.UPDATE_REPOSITORY_NAME)))
                .show();
    }

    private static String readableSize(long bytes) {
        return String.format(Locale.GERMANY, "%.1f MB", bytes / 1024d / 1024d);
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
