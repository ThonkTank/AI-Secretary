package de.thonktank.autosecretary;

import de.thonktank.autosecretary.ui.today.HeaderView;

import de.thonktank.autosecretary.presentation.alltasks.AllTasksAction;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksCoordinator;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksRequest;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksScreenState;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksUiState;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksViewModel;
import de.thonktank.autosecretary.presentation.legacy.LegacyStateFlowBinder;
import de.thonktank.autosecretary.presentation.navigation.AppDestination;
import de.thonktank.autosecretary.presentation.navigation.AppNavigator;
import de.thonktank.autosecretary.presentation.navigation.TaskEditorNavigator;
import de.thonktank.autosecretary.presentation.options.OptionsAction;
import de.thonktank.autosecretary.presentation.options.OptionsRequest;
import de.thonktank.autosecretary.presentation.options.OptionsScreenState;
import de.thonktank.autosecretary.presentation.options.OptionsViewModel;
import de.thonktank.autosecretary.presentation.shell.AppShellAction;
import de.thonktank.autosecretary.presentation.shell.AppShellScreenState;
import de.thonktank.autosecretary.presentation.shell.AppShellViewModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayRequest;
import de.thonktank.autosecretary.presentation.today.TodayScreenState;
import de.thonktank.autosecretary.presentation.today.TodayViewModel;
import de.thonktank.autosecretary.presentation.today.TimelineTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;

import android.Manifest;
import android.animation.LayoutTransition;
import android.app.AlertDialog;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.update.presentation.UpdateDialogs;
import de.thonktank.autosecretary.update.presentation.UpdatePlatform;

/** Lifecycle host for the state-driven dashboard view hierarchy. */
public class MainActivity extends ComponentActivity {
    public static final String CONFIRM_TASK = "confirm_task";
    public static final String CONFIRM_TASK_TITLE = "confirm_task_title";
    public static final String OPEN_EDITOR = "open_editor";
    private AppContainer container;
    private TodayViewModel todayViewModel;
    private AppShellViewModel shellViewModel;
    private TaskEditorViewModel editorViewModel;
    private AllTasksViewModel allTasksViewModel;
    private OptionsViewModel optionsViewModel;
    private AppNavigator appNavigator;
    private UpdateDialogs updateDialogs;
    private UpdatePlatform updatePlatform;
    private AppShellScreenState shellState;
    private TodayScreenState todayState;
    private OptionsScreenState optionsState;
    private ForestBackdropView forest;
    private HeaderView header;
    private FooterNavigationView footer;
    private ScrollView scroll;
    private DashboardRenderer renderer;
    private FrameLayout root;
    private LinearLayout dashboardScreen;
    private LinearLayout dashboardContent;
    private AllTasksUiState allTasksState = AllTasksUiState.empty();
    private TaskEditorScreenState editorState = new TaskEditorScreenState(
            EditorUiState.closed(), java.util.Collections.emptyList());
    private String handledAllTasksRequestId;
    private String handledEditorRequestId;
    private String handledOptionsRequestId;
    private String handledTodayRequestId;
    private String handledRewardEffectId;
    private TaskEditorCoordinator editorCoordinator;
    private int systemTopInset;
    private final RewardAnchorRegistry rewardAnchors = new RewardAnchorRegistry();
    private RewardAnimator rewardAnimator;

    private final ActivityResultLauncher<String> calendarPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                syncCalendarPermission();
            });

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted ->
                    openExactAlarmPermissionIfNeeded());

    private final ActivityResultLauncher<Intent> installPermission = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (optionsViewModel != null && updatePlatform != null) {
                    handledOptionsRequestId = null;
                    optionsViewModel.dispatch(OptionsAction.installPermissionResult(
                            updatePlatform.canInstallPackages()));
                }
            });

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (PresentationTrace.enabled()) PresentationTrace.emit("main-host", "create",
                "saved=" + (savedInstanceState != null));
        container = AutoSecretaryApplication.from(this).container();
        container.timers.reconcile();
        EdgeToEdge.enable(this);
        buildShell();
        editorViewModel = new ViewModelProvider(this,
                new TaskEditorViewModel.Factory(container)).get(TaskEditorViewModel.class);
        appNavigator = new TaskEditorNavigator(editorViewModel, this::prepareEditorFlight);
        shellViewModel = new ViewModelProvider(this,
                new AppShellViewModel.Factory(container)).get(AppShellViewModel.class);
        todayViewModel = new ViewModelProvider(this,
                new TodayViewModel.Factory(container, appNavigator)).get(TodayViewModel.class);
        allTasksViewModel = new ViewModelProvider(this,
                new AllTasksViewModel.Factory(container, appNavigator)).get(AllTasksViewModel.class);
        optionsViewModel = new ViewModelProvider(this,
                new OptionsViewModel.Factory(container)).get(OptionsViewModel.class);
        shellState = shellViewModel.state().getValue();
        todayState = todayViewModel.state().getValue();
        optionsState = optionsViewModel.state().getValue();
        AllTasksCoordinator allTasks = new AllTasksCoordinator(allTasksViewModel);
        renderer = new DashboardRenderer(this, scroll, dashboardContent,
                todayViewModel::dispatch,
                optionsViewModel::dispatch, versionName(),
                rewardAnchors, allTasks);
        editorCoordinator = new TaskEditorCoordinator(this, root, dashboardScreen,
                new TaskEditorView.Listener() {
                    @Override public void onDraftChanged(EditorUiState draft) {
                        editorViewModel.dispatch(TaskEditorAction.draftChanged(draft));
                    }
                    @Override public void onSave(EditorUiState draft) {
                        editorViewModel.dispatch(TaskEditorAction.save(draft));
                    }
                    @Override public void onDelete(String taskId) {
                        editorViewModel.dispatch(TaskEditorAction.delete(taskId));
                    }
                    @Override public void onDismiss() {
                        editorViewModel.dispatch(TaskEditorAction.dismiss());
                    }
                });
        updateDialogs = new AndroidUpdateDialogs(this);
        updatePlatform = new AndroidUpdatePlatform(this, installPermission,
                container.updateInstaller, container.logger,
                container.updateConfiguration.repositoryOwner,
                container.updateConfiguration.repositoryName);
        LegacyStateFlowBinder.observe(this, shellViewModel.state(), this::renderShellState);
        LegacyStateFlowBinder.observe(this, todayViewModel.state(), this::renderTodayState);
        LegacyStateFlowBinder.observe(this, editorViewModel.state(), this::renderEditorState);
        LegacyStateFlowBinder.observe(this, allTasksViewModel.state(),
                this::renderAllTasksState);
        LegacyStateFlowBinder.observe(this, optionsViewModel.state(), this::renderOptionsState);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (editorCoordinator != null && editorCoordinator.handleBack()) return;
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
        handleLaunchIntent();
        syncCalendarPermission();
    }

    @Override protected void onResume() {
        super.onResume();
        if (PresentationTrace.enabled()) PresentationTrace.emit("main-host", "resume", "");
        if (todayViewModel != null) {
            syncCalendarPermission();
            container.clockInvalidations.materializeForeground();
        }
        if (optionsViewModel != null) optionsViewModel.dispatch(OptionsAction.resumed());
        if (container != null) container.timers.refreshCapabilities();
    }

    @Override protected void onPause() {
        if (PresentationTrace.enabled()) PresentationTrace.emit("main-host", "pause", "");
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (PresentationTrace.enabled()) PresentationTrace.emit("main-host", "window-focus",
                "value=" + hasFocus);
    }

    @Override protected void onDestroy() {
        if (PresentationTrace.enabled()) PresentationTrace.emit("main-host", "destroy", "");
        if (rewardAnimator != null) rewardAnimator.dispose();
        if (editorCoordinator != null) editorCoordinator.dispose();
        super.onDestroy();
    }

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
        dashboardContent = content;
        content.setId(R.id.dashboard_content);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutTransition(new LayoutTransition());
        content.getLayoutTransition().setDuration(MotionTokens.standard().stateChangeDurationMs);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        footer = new FooterNavigationView(this, destination -> {
            if (shellViewModel != null)
                shellViewModel.dispatch(AppShellAction.destinationSelected(destination));
        });
        screen.addView(footer, new LinearLayout.LayoutParams(-1,
                getResources().getDimensionPixelSize(R.dimen.footer_height)));
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

    private void renderShellState(AppShellScreenState state) {
        if (state == null) return;
        shellState = state;
        render();
    }

    private void renderTodayState(TodayScreenState state) {
        if (state == null) return;
        todayState = state;
        render();
        handleTodayRequest(state.firstRequest());
        handleRewardEffects(state.rewards);
    }

    private void render() {
        if (shellState == null || todayState == null || optionsState == null) return;
        if (PresentationTrace.enabled()) PresentationTrace.emit("dashboard", "render",
                "navigation=" + shellState.navigation + " loading=" + todayState.loading
                        + " editorOpen=" + editorState.content.open);
        forest.setPalette(shellState.palette);
        header.bind(container.clock.time(), shellState.palette, todayState.today().xpProgress);
        footer.bind(shellState.navigation, shellState.palette);
        renderer.render(shellState, todayState, allTasksState, optionsState);
        boolean light = luminance(shellState.palette.background) > .55;
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(),
                getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
        editorCoordinator.render(editorState.content, shellState.palette,
                container.clock.today());
    }

    private void openEditorWithFlight() {
        if (appNavigator != null) appNavigator.navigate(AppDestination.newTaskFromHeader());
    }

    private void prepareEditorFlight() {
        if (renderer == null || shellState == null
                || shellState.navigation != NavigationDestination.TODAY) return;
        editorCoordinator.deferNextOpen();
        renderer.animateEditorTransition(editorCoordinator::completeDeferredOpen);
    }

    private void renderAllTasksState(AllTasksScreenState state) {
        if (state == null) return;
        allTasksState = state.content;
        render();
        handleAllTasksRequest(state.firstRequest());
    }

    private void handleAllTasksRequest(AllTasksRequest request) {
        if (request == null || request.id.equals(handledAllTasksRequestId)) return;
        handledAllTasksRequestId = request.id;
        if (request.kind == AllTasksRequest.Kind.ERROR) {
            new AlertDialog.Builder(this).setTitle(R.string.error_title)
                    .setMessage(request.message).setPositiveButton(R.string.okay,
                            (dialog, which) -> acknowledgeAllTasksRequest(request.id))
                    .setOnCancelListener(dialog -> acknowledgeAllTasksRequest(request.id)).show();
        } else if (request.kind == AllTasksRequest.Kind.INFO) {
            Toast.makeText(this, request.message, Toast.LENGTH_LONG).show();
            acknowledgeAllTasksRequest(request.id);
        } else if (request.kind == AllTasksRequest.Kind.CONFIRM_DELETE
                && request.taskId != null) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete_task_title, request.title))
                    .setMessage(R.string.delete_task_loss)
                    .setNegativeButton(R.string.keep,
                            (dialog, which) -> acknowledgeAllTasksRequest(request.id))
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        handledAllTasksRequestId = null;
                        allTasksViewModel.dispatch(AllTasksAction.confirmDelete(request.id));
                    }).setOnCancelListener(
                            dialog -> acknowledgeAllTasksRequest(request.id)).show();
        } else acknowledgeAllTasksRequest(request.id);
    }

    private void acknowledgeAllTasksRequest(String requestId) {
        if (requestId.equals(handledAllTasksRequestId)) handledAllTasksRequestId = null;
        allTasksViewModel.dispatch(AllTasksAction.acknowledgeRequest(requestId));
    }

    private void renderEditorState(TaskEditorScreenState state) {
        if (state == null) return;
        editorState = state;
        if (shellState != null)
            editorCoordinator.render(state.content, shellState.palette, container.clock.today());
        TaskEditorRequest request = state.firstRequest();
        if (request == null) {
            handledEditorRequestId = null;
            return;
        }
        if (request.id.equals(handledEditorRequestId)) return;
        handledEditorRequestId = request.id;
        new AlertDialog.Builder(this).setTitle(R.string.error_title)
                .setMessage(request.message).setPositiveButton(R.string.okay,
                        (dialog, which) -> acknowledgeEditorRequest(request.id))
                .setOnCancelListener(dialog -> acknowledgeEditorRequest(request.id)).show();
    }

    private void acknowledgeEditorRequest(String requestId) {
        if (requestId.equals(handledEditorRequestId)) handledEditorRequestId = null;
        editorViewModel.dispatch(TaskEditorAction.acknowledgeRequest(requestId));
    }

    private void renderOptionsState(OptionsScreenState state) {
        if (state == null) return;
        optionsState = state;
        render();
        handleOptionsRequest(state.firstRequest());
    }

    private void handleOptionsRequest(OptionsRequest request) {
        if (request == null) {
            handledOptionsRequestId = null;
            return;
        }
        if (request.id.equals(handledOptionsRequestId)) return;
        handledOptionsRequestId = request.id;
        if (request.kind == OptionsRequest.Kind.REQUEST_CALENDAR_PERMISSION) {
            calendarPermission.launch(Manifest.permission.READ_CALENDAR);
            acknowledgeOptionsRequest(request.id);
        } else if (request.kind == OptionsRequest.Kind.OPEN_APP_SETTINGS) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
            acknowledgeOptionsRequest(request.id);
        } else if (request.kind == OptionsRequest.Kind.UPDATE_AVAILABLE
                && request.update != null) {
            updateDialogs.showAvailable(request.update,
                    () -> optionsViewModel.dispatch(OptionsAction.updatePostponed(
                            request.id, request.update)),
                    () -> optionsViewModel.dispatch(OptionsAction.updateAccepted(
                            request.id, request.update)),
                    () -> acknowledgeOptionsRequest(request.id));
        } else if (request.kind == OptionsRequest.Kind.INSTALL_UPDATE
                && request.verified != null) {
            if (updatePlatform.canInstallPackages()) {
                if (updatePlatform.openInstaller(request.verified))
                    acknowledgeOptionsRequest(request.id);
                else optionsViewModel.dispatch(OptionsAction.installerFailed(request.id));
            } else {
                updateDialogs.showInstallPermission(updatePlatform::openInstallSettings,
                        () -> acknowledgeOptionsRequest(request.id));
            }
        } else if (request.kind == OptionsRequest.Kind.UPDATE_ERROR) {
            updateDialogs.showError(request.message, () -> {
                updatePlatform.openReleases();
                acknowledgeOptionsRequest(request.id);
            }, () -> acknowledgeOptionsRequest(request.id));
        } else acknowledgeOptionsRequest(request.id);
    }

    private void acknowledgeOptionsRequest(String requestId) {
        if (requestId.equals(handledOptionsRequestId)) handledOptionsRequestId = null;
        optionsViewModel.dispatch(OptionsAction.acknowledgeRequest(requestId));
    }

    private void handleTodayRequest(TodayRequest request) {
        if (request == null) {
            handledTodayRequestId = null;
            return;
        }
        if (request.id.equals(handledTodayRequestId)) return;
        handledTodayRequestId = request.id;
        if (request.kind == TodayRequest.Kind.ERROR) {
            new AlertDialog.Builder(this).setTitle(R.string.error_title)
                    .setMessage(request.message)
                    .setPositiveButton(R.string.okay,
                            (dialog, which) -> acknowledgeTodayRequest(request.id))
                    .setOnCancelListener(dialog -> acknowledgeTodayRequest(request.id)).show();
        } else if (request.kind == TodayRequest.Kind.INFO) {
            Toast.makeText(this, request.message, Toast.LENGTH_LONG).show();
            acknowledgeTodayRequest(request.id);
        } else if (request.kind == TodayRequest.Kind.TASK_MENU) {
            new AlertDialog.Builder(this).setTitle(request.title)
                    .setItems(new String[]{getString(R.string.task_edit),
                            getString(R.string.task_move), getString(R.string.task_delete)},
                            (dialog, which) -> {
                                if (which == 0) todayViewModel.dispatch(
                                        TodayAction.editTask(request.id));
                                else if (which == 1) todayViewModel.dispatch(
                                        TodayAction.requestMoveTask(request.id));
                                else todayViewModel.dispatch(
                                        TodayAction.requestDeleteTask(request.id));
                            })
                    .setOnCancelListener(dialog -> acknowledgeTodayRequest(request.id)).show();
        } else if (request.kind == TodayRequest.Kind.CHOOSE_MOVE) {
            TaskSlot[] slots = TaskSlot.values();
            new AlertDialog.Builder(this).setTitle(R.string.task_move)
                    .setSingleChoiceItems(slotLabels(), request.target.slot.ordinal(),
                            (dialog, which) -> {
                                todayViewModel.dispatch(TodayAction.moveTask(
                                        request.id, slots[which]));
                                dialog.dismiss();
                            })
                    .setNegativeButton(R.string.cancel,
                            (dialog, which) -> acknowledgeTodayRequest(request.id))
                    .setOnCancelListener(dialog -> acknowledgeTodayRequest(request.id)).show();
        } else if (request.kind == TodayRequest.Kind.CONFIRM_DELETE) {
            String loss = request.routine ? getString(R.string.delete_routine_loss)
                    : getString(R.string.delete_task_loss);
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete_task_title, request.title))
                    .setMessage(loss)
                    .setNegativeButton(R.string.keep,
                            (dialog, which) -> acknowledgeTodayRequest(request.id))
                    .setPositiveButton(R.string.delete, (dialog, which) ->
                            todayViewModel.dispatch(
                                    TodayAction.confirmDeleteTask(request.id)))
                    .setOnCancelListener(dialog -> acknowledgeTodayRequest(request.id)).show();
        } else if (request.kind == TodayRequest.Kind.CONFIRM_CLOSE) {
            new AlertDialog.Builder(this).setTitle(R.string.close_task_title)
                    .setMessage(getString(R.string.close_task_message, request.title))
                    .setNegativeButton(R.string.cancel,
                            (dialog, which) -> acknowledgeTodayRequest(request.id))
                    .setPositiveButton(R.string.condition_met, (dialog, which) ->
                            todayViewModel.dispatch(
                                    TodayAction.confirmCloseTask(request.id)))
                    .setOnCancelListener(dialog -> acknowledgeTodayRequest(request.id)).show();
        } else if (request.kind == TodayRequest.Kind.REQUEST_TIMER_PERMISSIONS) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.timer_permission_title)
                    .setMessage(R.string.timer_permission_message)
                    .setNegativeButton(R.string.continue_action,
                            (dialog, which) -> acknowledgeTodayRequest(request.id))
                    .setPositiveButton(R.string.timer_permission_open, (dialog, which) -> {
                        if (consumeTodayRequest(request.id,
                                TodayRequest.Kind.REQUEST_TIMER_PERMISSIONS))
                            requestTimerPermissions();
                    })
                    .setOnCancelListener(dialog -> acknowledgeTodayRequest(request.id)).show();
        } else acknowledgeTodayRequest(request.id);
    }

    private void acknowledgeTodayRequest(String requestId) {
        if (requestId.equals(handledTodayRequestId)) handledTodayRequestId = null;
        todayViewModel.dispatch(TodayAction.acknowledgeRequest(requestId));
    }

    private boolean consumeTodayRequest(String requestId, TodayRequest.Kind kind) {
        TodayRequest pending = todayViewModel.state().getValue().request(requestId);
        if (pending == null || pending.kind != kind) return false;
        acknowledgeTodayRequest(requestId);
        return true;
    }

    private void requestTimerPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        openExactAlarmPermissionIfNeeded();
    }

    private void openExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        AlarmManager alarms = getSystemService(AlarmManager.class);
        if (alarms.canScheduleExactAlarms()) return;
        startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + getPackageName())));
    }

    private void handleRewardEffects(RewardEffectQueue.Snapshot snapshot) {
        if (snapshot == null || shellState == null || rewardAnimator == null) return;
        RewardEffect effect = snapshot.first();
        if (effect == null) {
            handledRewardEffectId = null;
            return;
        }
        if (effect.id.equals(handledRewardEffectId)) return;
        handledRewardEffectId = effect.id;
        rewardAnimator.play(effect, shellState.palette, systemTopInset, () -> {
            handledRewardEffectId = null;
            todayViewModel.dispatch(TodayAction.acknowledgeReward(effect.id));
        });
    }

    private void syncCalendarPermission() {
        if (optionsViewModel == null) return;
        boolean granted = checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
        optionsViewModel.dispatch(OptionsAction.permissionObserved(granted,
                shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)));
    }

    private void handleLaunchIntent() {
        String confirmTask = getIntent().getStringExtra(CONFIRM_TASK);
        String confirmTitle = getIntent().getStringExtra(CONFIRM_TASK_TITLE);
        getIntent().removeExtra(CONFIRM_TASK);
        getIntent().removeExtra(CONFIRM_TASK_TITLE);
        if (confirmTask != null)
            todayViewModel.dispatch(TodayAction.requestClose(confirmTask,
                    confirmTitle == null ? getString(R.string.this_project) : confirmTitle));
        boolean openEditor = getIntent().getBooleanExtra(OPEN_EDITOR, false);
        getIntent().removeExtra(OPEN_EDITOR);
        if (openEditor) appNavigator.navigate(AppDestination.newTask());
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
