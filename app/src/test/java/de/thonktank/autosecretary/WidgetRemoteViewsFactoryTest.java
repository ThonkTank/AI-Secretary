package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Parcel;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowApplication;

import java.time.LocalTime;
import de.thonktank.autosecretary.calendar.CalendarResult;
import de.thonktank.autosecretary.widget.WidgetPresenter;
import de.thonktank.autosecretary.widget.WidgetUiModel;

@RunWith(RobolectricTestRunner.class)
public final class WidgetRemoteViewsFactoryTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Test @Config(sdk = 26)
    public void api26UsesDynamicSharedForestAndRenderedChrome() {
        WidgetForestCache cache = new WidgetForestCache();
        WidgetUiModel model = model(WidgetSizeClassifier.Size.SMALL, DayPalette.Mode.DARK);
        View applied = new WidgetRemoteViewsFactory(context, cache).create(model)
                .apply(context, new FrameLayout(context));

        ImageView forest = applied.findViewById(R.id.widget_forest);
        Bitmap bitmap = ((BitmapDrawable) forest.getDrawable()).getBitmap();
        TextView action = applied.findViewById(R.id.widget_action);
        ImageView actionBackground = applied.findViewById(R.id.widget_action_background);
        assertNotNull(bitmap);
        assertNotNull(((BitmapDrawable) actionBackground.getDrawable()).getBitmap());
        Bitmap actionBitmap = cache.button(model.size, model.palette);
        assertNotSame(actionBitmap, cache.button(model.size,
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT)));
        assertTrue(bitmap.getAllocationByteCount() <= WidgetForestCache.MAX_BITMAP_BYTES);
        assertNull(action.getBackgroundTintList());
        assertEquals(model.palette.accentText, action.getCurrentTextColor());
    }

    @Test @Config(sdk = 35)
    public void api31PlusAppliesDynamicChromeAndStaysBelowBinderBudget() {
        WidgetUiModel model = model(WidgetSizeClassifier.Size.TALL, DayPalette.Mode.DARK);
        android.widget.RemoteViews remoteViews = new WidgetRemoteViewsFactory(context,
                new WidgetForestCache()).create(model);
        View applied = remoteViews.apply(context, new FrameLayout(context));
        ImageView actionBackground = applied.findViewById(R.id.widget_action_background);
        assertNotNull(((BitmapDrawable) actionBackground.getDrawable()).getBitmap());
        Parcel parcel = Parcel.obtain();
        try {
            remoteViews.writeToParcel(parcel, 0);
            assertTrue("RemoteViews parcel was " + parcel.dataSize() + " bytes",
                    parcel.dataSize() < 900_000);
        } finally {
            parcel.recycle();
        }
    }

    @Test @Config(sdk = 35)
    public void allFourLayoutsCanBeBoundAndApplied() {
        WidgetRemoteViewsFactory factory = new WidgetRemoteViewsFactory(context,
                new WidgetForestCache());
        for (WidgetSizeClassifier.Size size : WidgetSizeClassifier.Size.values()) {
            View applied = factory.create(model(size, DayPalette.Mode.AUTO))
                    .apply(context, new FrameLayout(context));
            assertNotNull(applied.findViewById(R.id.widget_root));
            assertEquals(context.getString(R.string.widget_open_dashboard),
                    applied.findViewById(R.id.widget_root).getContentDescription());
        }
    }

    @Test @Config(sdk = 35)
    public void canonicalLayoutsKeepVisualControlsInsideFortyEightDpTargets() {
        WidgetRemoteViewsFactory factory = new WidgetRemoteViewsFactory(context,
                new WidgetForestCache());
        for (WidgetSizeClassifier.Size size : new WidgetSizeClassifier.Size[]{
                WidgetSizeClassifier.Size.SMALL, WidgetSizeClassifier.Size.WIDE,
                WidgetSizeClassifier.Size.TALL}) {
            View applied = factory.create(model(size, DayPalette.Mode.AUTO))
                    .apply(context, new FrameLayout(context));
            View target = applied.findViewById(R.id.widget_action_target);
            View visual = applied.findViewById(R.id.widget_action_background);
            assertEquals(dp(48), target.getLayoutParams().height);
            assertEquals(dp(size == WidgetSizeClassifier.Size.TALL ? 40 : 38),
                    visual.getLayoutParams().height);
        }
    }

    @Test @Config(sdk = 35)
    public void widgetDewUsesRenderedDesignArtworkInsteadOfUnicodeGlyphs() {
        View applied = new WidgetRemoteViewsFactory(context, new WidgetForestCache())
                .create(model(WidgetSizeClassifier.Size.WIDE, DayPalette.Mode.AUTO))
                .apply(context, new FrameLayout(context));
        ImageView dew = applied.findViewById(R.id.widget_step_dot_1);
        assertNotNull(((BitmapDrawable) dew.getDrawable()).getBitmap());
        assertEquals(dp(48), dew.getLayoutParams().width);
        assertEquals(dp(48), dew.getLayoutParams().height);
    }

    @Test @Config(sdk = 35)
    public void responsiveRowsBindCompactMetadataAndExposeTheFullAccessibleLabel() {
        View applied = new WidgetRemoteViewsFactory(context, new WidgetForestCache())
                .create(model(WidgetSizeClassifier.Size.WIDE, DayPalette.Mode.AUTO))
                .apply(context, new FrameLayout(context));

        TextView title = applied.findViewById(R.id.widget_step_text_1);
        TextView subtitle = applied.findViewById(R.id.widget_step_subtitle_1);
        View toggle = applied.findViewById(R.id.widget_step_dot_1);

        assertEquals("Duschen", title.getText().toString());
        assertEquals("3 Sätze · 8 Wiederholungen", subtitle.getText().toString());
        assertEquals(View.VISIBLE, subtitle.getVisibility());
        assertEquals(context.getString(R.string.widget_toggle_step,
                        "Duschen, 3 Sätze · 8 Wiederholungen"),
                toggle.getContentDescription());
    }

    @Test @Config(sdk = 26)
    public void allFourLayoutsAlsoBindOnMinimumApi() {
        WidgetForestCache cache = new WidgetForestCache();
        WidgetRemoteViewsFactory factory = new WidgetRemoteViewsFactory(context, cache);
        for (WidgetSizeClassifier.Size size : WidgetSizeClassifier.Size.values()) {
            View applied = factory.create(model(size, DayPalette.Mode.AUTO))
                    .apply(context, new FrameLayout(context));
            assertNotNull(applied.findViewById(R.id.widget_forest));
            if (size == WidgetSizeClassifier.Size.TALL
                    || size == WidgetSizeClassifier.Size.LARGE) {
                WidgetUiModel current = model(size, DayPalette.Mode.AUTO);
                ImageView calendar = applied.findViewById(R.id.widget_calendar_background);
                assertNotNull(((BitmapDrawable) calendar.getDrawable()).getBitmap());
                Bitmap leaf = cache.leaf(
                        size == WidgetSizeClassifier.Size.TALL ? 230 : 286,
                        84, true, current.palette);
                assertNotSame(leaf, cache.leaf(
                        size == WidgetSizeClassifier.Size.TALL ? 230 : 286,
                        84, true, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT)));
            }
        }
    }

    @Test @Config(sdk = 35)
    public void finalConditionActionLaunchesConfirmationInTheApp() {
        WidgetPresenter presenter = new WidgetPresenter(context);
        WidgetUiModel model = presenter.present(new WidgetPresenter.CycleData(
                        DashboardFixtures.ongoingWidgetDashboard(),
                        new CalendarResult.PermissionMissing(),
                        DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO)),
                WidgetSizeClassifier.Size.TALL);
        View applied = new WidgetRemoteViewsFactory(context, new WidgetForestCache())
                .create(model).apply(context, new FrameLayout(context));

        applied.findViewById(R.id.widget_action).performClick();

        ShadowApplication shadowApplication = Shadow.extract(context);
        Intent launched = shadowApplication.getNextStartedActivity();
        assertNotNull(launched);
        assertEquals("ongoing", launched.getStringExtra(MainActivity.CONFIRM_TASK));
        assertEquals("Praktikum", launched.getStringExtra(MainActivity.CONFIRM_TASK_TITLE));
    }

    @Test @Config(sdk = 35)
    public void forestRenderCacheIsBoundedBySizeAndPalette() {
        WidgetForestCache cache = new WidgetForestCache();
        DayPalette day = DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT);
        DayPalette night = DayPalette.at(LocalTime.NOON, DayPalette.Mode.DARK);

        Bitmap first = cache.get(WidgetSizeClassifier.Size.LARGE, day);
        Bitmap second = cache.get(WidgetSizeClassifier.Size.LARGE, day);
        Bitmap third = cache.get(WidgetSizeClassifier.Size.LARGE, night);

        assertSame(first, second);
        assertNotSame(first, third);
        assertEquals(2, cache.renderCount());
        assertTrue(first.getAllocationByteCount() <= WidgetForestCache.MAX_BITMAP_BYTES);
    }

    @Test @Config(sdk = 35, qualifiers = "xxhdpi")
    public void artworkUsesHostDensityWithoutExceedingTheExplicitBitmapBudget() {
        WidgetUiModel model = model(WidgetSizeClassifier.Size.LARGE, DayPalette.Mode.DARK);
        View applied = new WidgetRemoteViewsFactory(context, new WidgetForestCache())
                .create(model).apply(context, new FrameLayout(context));
        Bitmap forest = ((BitmapDrawable) ((ImageView) applied.findViewById(
                R.id.widget_forest)).getDrawable()).getBitmap();

        assertEquals(1032, forest.getWidth());
        assertEquals(1032, forest.getHeight());
        assertTrue(forest.getAllocationByteCount() <= WidgetForestCache.MAX_DENSITY_BITMAP_BYTES);
    }

    private WidgetUiModel model(WidgetSizeClassifier.Size size, DayPalette.Mode mode) {
        WidgetPresenter presenter = new WidgetPresenter(context);
        return presenter.present(new WidgetPresenter.CycleData(DashboardFixtures.widgetDashboard(),
                        new CalendarResult.Success(DashboardFixtures.calendarEvents()),
                        DayPalette.at(LocalTime.of(22, 0), mode)), size);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
