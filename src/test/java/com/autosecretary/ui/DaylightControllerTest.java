package com.autosecretary.ui;

import static org.junit.Assert.assertEquals;

import android.Manifest;
import android.app.Application;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.autosecretary.R;
import com.autosecretary.application.LocationPort;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public final class DaylightControllerTest {
    private TestActivity activity;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(TestActivity.class).create().start().resume().get();
        activity.getSharedPreferences("waldmorgen_ui", AppCompatActivity.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @After public void tearDown() { activity.finish(); }

    @Test
    public void locationObservationRunsOnlyBetweenStartAndStop() {
        Shadows.shadowOf((Application) activity.getApplication())
                .grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        FakeLocation location = new FakeLocation();
        DaylightController controller = controller(location, new AtomicInteger());
        controller.configure();

        controller.onStart();
        assertEquals(1, location.starts);
        location.deliver(new LocationPort.Position(52.5, 13.4));
        controller.onStop();
        assertEquals(1, location.stops);

        location.deliver(new LocationPort.Position(48.1, 11.5));
        assertEquals(1, location.deliveries);
    }

    @Test
    public void deniedPermissionRequestsOnceAndNeverStartsLocation() {
        Shadows.shadowOf((Application) activity.getApplication())
                .denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        FakeLocation location = new FakeLocation();
        AtomicInteger requests = new AtomicInteger();
        DaylightController controller = controller(location, requests);

        controller.configure();
        controller.configure();
        controller.onStart();

        assertEquals(1, requests.get());
        assertEquals(0, location.starts);
        controller.onStop();
    }

    @Test
    public void permissionResultWhileStoppedDoesNotStartObservation() {
        Application application = (Application) activity.getApplication();
        Shadows.shadowOf(application).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        FakeLocation location = new FakeLocation();
        DaylightController controller = controller(location, new AtomicInteger());
        controller.configure();

        Shadows.shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        controller.onLocationPermissionResult(true);
        assertEquals(0, location.starts);

        controller.onStart();
        assertEquals(1, location.starts);
        controller.onStop();
    }

    private DaylightController controller(FakeLocation location, AtomicInteger requests) {
        FrameLayout root = new FrameLayout(activity);
        return new DaylightController(activity, root, new DaylightBackdropView(activity, null),
                new TextView(activity), new TextView(activity), location,
                requests::incrementAndGet);
    }

    public static final class TestActivity extends AppCompatActivity {
        @Override protected void onCreate(Bundle state) {
            setTheme(R.style.Theme_AutoSecretary);
            super.onCreate(state);
        }
    }

    private static final class FakeLocation implements LocationPort {
        int starts;
        int stops;
        int deliveries;
        Consumer<Position> consumer;

        @Override public Position lastKnown() { return new Position(51.2, 6.7); }
        @Override public void start(Consumer<Position> listener) {
            starts++;
            consumer = listener;
        }
        @Override public void stop() {
            stops++;
            consumer = null;
        }
        void deliver(Position position) {
            if (consumer != null) {
                deliveries++;
                consumer.accept(position);
            }
        }
    }
}
