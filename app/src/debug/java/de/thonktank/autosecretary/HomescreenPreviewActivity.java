package de.thonktank.autosecretary;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.activity.EdgeToEdge;
import androidx.activity.ComponentActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.calendar.CalendarResult;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;
import de.thonktank.autosecretary.widget.WidgetPresenter;
import de.thonktank.autosecretary.widget.WidgetDashboardUiModel;
import de.thonktank.autosecretary.widget.WidgetUiModel;

/** Debug-only deterministic gallery for the ten supplied visual reference states. */
public final class HomescreenPreviewActivity extends ComponentActivity {
    private WidgetForestCache widgetCache;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String preview = getIntent().getStringExtra("preview");
        if (preview == null) preview = "full";
        if (preview.startsWith("widget-")) showWidget(preview.substring(7));
        else showPhone(preview);
    }

    private void showPhone(String preview) {
        EdgeToEdge.enable(this);
        LocalTime time = "empty".equals(preview) || "night".equals(preview)
                ? LocalTime.of(23, 50)
                : "evening".equals(preview) ? LocalTime.of(19, 35) : LocalTime.of(9, 40);
        DayPalette palette = DayPalette.at(time, DayPalette.Mode.AUTO);
        FrameLayout root = new FrameLayout(this);
        ForestBackdropView forest = new ForestBackdropView(this);
        forest.setPalette(palette);
        root.addView(forest, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout screen = new LinearLayout(this);
        screen.setId(R.id.dashboard_screen);
        screen.setOrientation(LinearLayout.VERTICAL);
        root.addView(screen, new FrameLayout.LayoutParams(-1, -1));
        HeaderView header = new HeaderView(this, () -> { });
        TodayUiModel dashboard = DebugPreviewFixtures.reference(preview);
        header.bind(time, palette, dashboard.xpProgress);
        screen.addView(header, new LinearLayout.LayoutParams(-1,
                getResources().getDimensionPixelSize(R.dimen.header_height)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        FooterNavigationView footer = new FooterNavigationView(this, ignored -> { });
        footer.bind(NavigationDestination.TODAY, palette);
        screen.addView(footer, new LinearLayout.LayoutParams(-1,
                getResources().getDimensionPixelSize(R.dimen.footer_height)));
        DashboardRenderer renderer = new DashboardRenderer(this, scroll, content,
                event -> { }, "preview");
        java.util.List<CalendarEventSnapshot> events = DebugPreviewFixtures.referenceCalendar(preview);
        renderer.render(new DashboardUiState(NavigationDestination.TODAY,
                        TodayUiModel.compose(dashboard, events),
                        CalendarUiState.from(new CalendarResult.Success(events)), palette,
                        CalendarPermissionStatus.GRANTED, false, Collections.emptySet(),
                        EditorUiState.closed()));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            screen.setPadding(0, bars.top, 0, 0);
            return insets;
        });
        setContentView(root);
        ViewCompat.requestApplyInsets(root);
    }

    private void showWidget(String name) {
        WidgetSizeClassifier.Size size = WidgetSizeClassifier.Size.valueOf(name.toUpperCase());
        LocalTime time = size == WidgetSizeClassifier.Size.LARGE ? LocalTime.of(23, 50)
                : size == WidgetSizeClassifier.Size.TALL ? LocalTime.of(19, 35) : LocalTime.of(9, 40);
        WidgetDashboardUiModel dashboard = DebugPreviewFixtures.widgetReference();
        CalendarResult calendar = new CalendarResult.Success(DebugPreviewFixtures.referenceCalendar("full"));
        WidgetPresenter presenter = new WidgetPresenter(this);
        WidgetUiModel model = presenter.present(new WidgetPresenter.CycleData(dashboard, calendar,
                DayPalette.at(time, DayPalette.Mode.AUTO)), size);
        widgetCache = new WidgetForestCache();
        View widget = new WidgetRemoteViewsFactory(this, widgetCache).create(model)
                .apply(this, new FrameLayout(this));
        int width = dp(size == WidgetSizeClassifier.Size.SMALL ? 160
                : size == WidgetSizeClassifier.Size.TALL ? 280 : 344);
        int height = dp(size == WidgetSizeClassifier.Size.SMALL || size == WidgetSizeClassifier.Size.WIDE
                ? 160 : 344);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xffdde2d8);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height, Gravity.CENTER);
        root.addView(widget, params);
        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (widgetCache != null) widgetCache.clear();
        widgetCache = null;
    }
}
