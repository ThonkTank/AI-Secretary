package com.autosecretary.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.autosecretary.application.GetTodayTimeline;
import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.presentation.R;
import com.autosecretary.presentation.databinding.FragmentTodayBinding;
import com.autosecretary.ui.editor.AddWorkItemDialogFragment;
import com.google.android.material.snackbar.Snackbar;

/** Today feature: timeline, permission state, daylight and local effects. */
public final class TodayFragment extends Fragment {
    private static final String UI_PREFERENCES = "waldmorgen_ui";
    private static final String CALENDAR_ASKED = "calendar_asked";
    private static final String EXPANDED = "today.expanded";
    private FragmentTodayBinding binding;
    private MainViewModel viewModel;
    private TodayPanelController panel;
    private DaylightController daylight;
    private TimelineRefreshScheduler timelineRefresh;
    private boolean dismissed;

    private final androidx.activity.result.ActivityResultLauncher<String> calendarPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                updateCalendarPermission();
                viewModel.reload();
                host().refreshWidgets();
            });
    private final androidx.activity.result.ActivityResultLauncher<String> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (daylight != null) daylight.onLocationPermissionResult(granted);
            });

    @Nullable @Override public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle state) {
        binding = FragmentTodayBinding.inflate(inflater, container, false);
        viewModel = FeatureViewModels.main(this);
        panel = new TodayPanelController(binding, host().timeProvider(),
                state != null && state.getBoolean(EXPANDED), new TodayPanelController.Actions() {
                    @Override public void complete(String id) { viewModel.complete(id); }
                    @Override public void setStepCompleted(
                            String itemId, String stepId, boolean completed) {
                        viewModel.setStepCompleted(itemId, stepId, completed);
                    }
                    @Override public void move(
                            String id, MoveWorkItemUseCase.Direction direction) {
                        viewModel.move(id, direction);
                    }
                    @Override public void omitToday(String id) { viewModel.omitToday(id); }
                    @Override public void undo() { viewModel.undo(); }
                });
        timelineRefresh = new TimelineRefreshScheduler(host().timeProvider(), () -> {
            if (viewModel != null) viewModel.reload();
            host().refreshWidgets();
        });
        daylight = new DaylightController((androidx.appcompat.app.AppCompatActivity) requireActivity(),
                binding.Root, binding.DaylightBackdrop, binding.ThemeMode, binding.Greeting,
                host().locationPort(), host().timeProvider(),
                () -> locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION),
                this::refreshPalette);
        daylight.configure();
        binding.ThemeMode.setOnClickListener(view -> daylight.cycleMode());
        binding.UndoAction.setOnClickListener(view -> viewModel.undo());
        binding.AddFab.setOnClickListener(view -> showAdd());
        binding.CalendarPermissionAction.setOnClickListener(view -> requestCalendar());
        binding.CalendarPermissionSkip.setOnClickListener(view -> {
            dismissed = true;
            updateCalendarPermission();
        });
        updateCalendarPermission();
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.effects().observe(getViewLifecycleOwner(), this::handleEffect);
    }

    @Override public void onStart() {
        super.onStart();
        if (timelineRefresh != null) timelineRefresh.start();
        if (daylight != null) daylight.onStart();
    }

    @Override public void onResume() {
        super.onResume();
        updateCalendarPermission();
        if (viewModel != null) viewModel.reload();
    }

    @Override public void onStop() {
        if (timelineRefresh != null) timelineRefresh.stop();
        if (daylight != null) daylight.onStop();
        super.onStop();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(EXPANDED, panel != null && panel.expanded());
        super.onSaveInstanceState(outState);
    }

    @Override public void onDestroyView() {
        if (timelineRefresh != null) timelineRefresh.close();
        timelineRefresh = null;
        daylight = null;
        panel = null;
        binding = null;
        super.onDestroyView();
    }

    private void render(MainUiState state) {
        if (binding == null || !(state instanceof MainUiState.Ready ready)) return;
        var timeline = new GetTodayTimeline(host().timeProvider()).execute(ready.dashboard());
        timelineRefresh.update(timeline.nextRefreshAt());
        Dashboard dashboard = UiModelMapper.dashboard(ready.dashboard(), timeline,
                host().timeProvider().zone(), getString(R.string.calendar_private));
        String undo = ready.dashboard().undoLabel();
        binding.UndoAction.setVisibility(undo == null ? View.GONE : View.VISIBLE);
        binding.UndoAction.setContentDescription(undo);
        binding.Root.setContentDescription("Auto Secretary · Datenbank " + host().databaseVersion());
        panel.render(dashboard, ready.dashboard(), ready.dashboard().conflicts(), undo);
        refreshPalette();
        updateCalendarPermission();
    }

    private void handleEffect(MainUiEffect effect) {
        if (effect == null) return;
        viewModel.consumeEffect(effect.id());
        if (effect instanceof MainUiEffect.Completion) {
            binding.Celebration.burst();
            binding.Celebration.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                    ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.KEYBOARD_TAP);
            Snackbar.make(binding.Root, R.string.done, Snackbar.LENGTH_SHORT).show();
        } else if (effect instanceof MainUiEffect.Error error) {
            Toast.makeText(requireContext(), error.message(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshPalette() {
        if (binding == null) return;
        boolean evening = binding.DaylightBackdrop.usesEveningPalette();
        panel.setEvening(evening);
        binding.Greeting.setTextColor(evening ? 0xFFBCAB8C
                : ContextCompat.getColor(requireContext(), R.color.marker));
    }

    private void updateCalendarPermission() {
        if (binding == null) return;
        boolean granted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
        boolean permissionMissing = viewModel != null
                && viewModel.state().getValue() instanceof MainUiState.Ready ready
                && ready.dashboard().calendarPermissionMissing();
        binding.CalendarPermissionCard.setVisibility(
                !dismissed && (!granted || permissionMissing) ? View.VISIBLE : View.GONE);
        if (granted) return;
        boolean asked = requireContext().getSharedPreferences(
                UI_PREFERENCES, android.content.Context.MODE_PRIVATE)
                .getBoolean(CALENDAR_ASKED, false);
        boolean denied = asked && !shouldShowRequestPermissionRationale(
                Manifest.permission.READ_CALENDAR);
        binding.CalendarPermissionTitle.setText(denied
                ? "Ohne Kalender plant die App blind." : "Kalender als Umgebung");
        binding.CalendarPermissionBody.setText(denied
                ? "Du kannst den Zugriff in den Einstellungen erlauben."
                : "Termine bleiben unverändert und helfen nur bei der freien Zeit.");
        binding.CalendarPermissionSkip.setVisibility(denied ? View.GONE : View.VISIBLE);
        binding.CalendarPermissionAction.setText(denied ? "Einstellungen" : "Kalender freigeben");
    }

    private void requestCalendar() {
        boolean asked = requireContext().getSharedPreferences(
                UI_PREFERENCES, android.content.Context.MODE_PRIVATE)
                .getBoolean(CALENDAR_ASKED, false);
        if (asked && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + requireContext().getPackageName())));
            return;
        }
        requireContext().getSharedPreferences(UI_PREFERENCES,
                android.content.Context.MODE_PRIVATE).edit()
                .putBoolean(CALENDAR_ASKED, true).apply();
        calendarPermission.launch(Manifest.permission.READ_CALENDAR);
    }

    private void showAdd() {
        if (getParentFragmentManager().findFragmentByTag(AddWorkItemDialogFragment.TAG) == null) {
            new AddWorkItemDialogFragment().show(
                    getParentFragmentManager(), AddWorkItemDialogFragment.TAG);
        }
    }

    private FeatureHost host() { return (FeatureHost) requireActivity(); }
}
