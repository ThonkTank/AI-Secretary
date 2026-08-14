package com.autosecretary.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.application.LocationPort;
import com.autosecretary.application.TimeProvider;
import com.autosecretary.platform.CalendarChangeObserver;
import com.autosecretary.presentation.R;
import com.autosecretary.presentation.databinding.ActivityMainBinding;
import com.autosecretary.ui.editor.EditorResultContract;
import com.autosecretary.ui.settings.PlanningSettingsResultContract;
import com.autosecretary.ui.update.UpdateUiEffect;
import com.autosecretary.ui.FeatureHost;
import com.autosecretary.ui.FeatureViewModelFactoryOwner;
import com.autosecretary.ui.MainUiState;
import com.autosecretary.ui.MainViewModel;
import com.autosecretary.ui.Surface;
import com.autosecretary.ui.TodayFragment;
import com.autosecretary.ui.WorkItemsFragment;
import com.autosecretary.ui.AiFragment;

/** Thin host: typed navigation, feature hosting and app-owned composition callbacks. */
public final class MainActivity extends AppCompatActivity
        implements FeatureViewModelFactoryOwner, FeatureHost {
    private ActivityMainBinding binding;
    private ViewModelProvider.Factory featureFactory;
    private MainViewModel viewModel;
    private AutoSecretaryApplication application;
    private CalendarChangeObserver calendarObserver;
    private Bundle defaults;
    private Surface renderedSurface;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        defaults = state;
        application = AutoSecretaryApplication.from(this);
        featureFactory = application.viewModelFactory(this, state);
        viewModel = new ViewModelProvider(this, featureFactory).get(MainViewModel.class);
        binding.NavToday.setOnClickListener(view -> viewModel.selectSurface(Surface.TODAY));
        binding.NavAll.setOnClickListener(view -> viewModel.selectSurface(Surface.ALL));
        binding.NavAi.setOnClickListener(view -> viewModel.selectSurface(Surface.AI));
        viewModel.state().observe(this, this::render);
        getSupportFragmentManager().setFragmentResultListener(
                EditorResultContract.CHANGED, this, (key, result) -> onFeatureDataChanged());
        getSupportFragmentManager().setFragmentResultListener(
                PlanningSettingsResultContract.CHANGED, this,
                (key, result) -> onFeatureDataChanged());
        calendarObserver = new CalendarChangeObserver(this, application.executors().main(), () -> {
            viewModel.reload();
            refreshWidgets();
        });
    }

    @Override protected void onStart() {
        super.onStart();
        calendarObserver.start();
    }

    @Override protected void onResume() {
        super.onResume();
        viewModel.reload();
    }

    @Override protected void onStop() {
        calendarObserver.stop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        calendarObserver.close();
        super.onDestroy();
    }

    private void render(MainUiState state) {
        if (state == null) return;
        renderNavigation(state.surface());
        renderFeature(state.surface());
    }

    private void onFeatureDataChanged() {
        viewModel.reload();
        refreshWidgets();
    }

    private void renderNavigation(Surface surface) {
        binding.NavToday.setTextColor(color(surface == Surface.TODAY));
        binding.NavAll.setTextColor(color(surface == Surface.ALL));
        binding.NavAi.setTextColor(color(surface == Surface.AI));
    }

    private int color(boolean selected) {
        return ContextCompat.getColor(this, selected ? R.color.ink_secondary : R.color.marker);
    }

    private void renderFeature(Surface surface) {
        if (surface == renderedSurface) return;
        renderedSurface = surface;
        Fragment feature = switch (surface) {
            case TODAY -> new TodayFragment();
            case ALL -> new WorkItemsFragment();
            case AI -> new AiFragment();
        };
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.FeatureHost, feature, "feature:" + surface.name())
                .commit();
    }

    @Override public ViewModelProvider.Factory featureViewModelFactory() {
        if (featureFactory == null) {
            featureFactory = AutoSecretaryApplication.from(this)
                    .viewModelFactory(this, defaults);
        }
        return featureFactory;
    }

    @Override public TimeProvider timeProvider() { return application.graph().clock(); }
    @Override public LocationPort locationPort() { return application.location(); }
    @Override public void refreshWidgets() { application.graph().refreshWidgets(); }
    @Override public boolean updatesEnabled() { return !com.autosecretary.BuildConfig.DEBUG; }
    @Override public boolean canInstallPackages() {
        return getPackageManager().canRequestPackageInstalls();
    }
    @Override public void handleUpdateEffect(UpdateUiEffect effect) {
        if (effect instanceof UpdateUiEffect.OpenUnknownSourcesSettings) {
            startActivity(application.updateSettingsIntent(this));
        } else if (effect instanceof UpdateUiEffect.OpenInstaller installer) {
            startActivity(application.updateInstallerIntent(this, installer.update()));
        }
    }
    @Override public int databaseVersion() { return application.databaseVersion(); }
}
