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
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.LocalTime;
import de.thonktank.autosecretary.calendar.CalendarResult;

@RunWith(RobolectricTestRunner.class)
public final class WidgetRemoteViewsFactoryTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Test @Config(sdk = 26)
    public void api26UsesDynamicSharedForestAndDocumentedStaticChromeFallback() {
        WidgetForestCache cache = new WidgetForestCache();
        WidgetUiModel model = model(WidgetSizeClassifier.Size.SMALL, DayPalette.Mode.DARK);
        View applied = new WidgetRemoteViewsFactory(context, cache).create(model)
                .apply(context, new FrameLayout(context));

        ImageView forest = applied.findViewById(R.id.widget_forest);
        Bitmap bitmap = ((BitmapDrawable) forest.getDrawable()).getBitmap();
        TextView action = applied.findViewById(R.id.widget_action);
        assertNotNull(bitmap);
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
        TextView action = applied.findViewById(R.id.widget_action);
        assertEquals(model.palette.accent,
                action.getBackgroundTintList().getDefaultColor());
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

    @Test @Config(sdk = 26)
    public void allFourLayoutsAlsoBindOnMinimumApi() {
        WidgetRemoteViewsFactory factory = new WidgetRemoteViewsFactory(context,
                new WidgetForestCache());
        for (WidgetSizeClassifier.Size size : WidgetSizeClassifier.Size.values()) {
            View applied = factory.create(model(size, DayPalette.Mode.AUTO))
                    .apply(context, new FrameLayout(context));
            assertNotNull(applied.findViewById(R.id.widget_forest));
        }
    }

    @Test @Config(sdk = 35)
    public void finalConditionActionLaunchesConfirmationInTheApp() {
        WidgetPresenter presenter = new WidgetPresenter(context);
        WidgetUiModel model = presenter.present(new WidgetPresenter.CycleData(
                        new DashboardState(0, java.util.Collections.singletonList(
                                DashboardFixtures.ongoingTask())),
                        new CalendarResult.PermissionMissing(),
                        DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO)),
                WidgetSizeClassifier.Size.TALL);
        View applied = new WidgetRemoteViewsFactory(context, new WidgetForestCache())
                .create(model).apply(context, new FrameLayout(context));

        applied.findViewById(R.id.widget_action).performClick();

        Intent launched = Shadows.shadowOf((android.app.Application) context)
                .getNextStartedActivity();
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

    private WidgetUiModel model(WidgetSizeClassifier.Size size, DayPalette.Mode mode) {
        WidgetPresenter presenter = new WidgetPresenter(context);
        return presenter.present(new WidgetPresenter.CycleData(DashboardFixtures.fullDashboard(),
                        new CalendarResult.Success(DashboardFixtures.calendarEvents()),
                        DayPalette.at(LocalTime.of(22, 0), mode)), size);
    }
}
