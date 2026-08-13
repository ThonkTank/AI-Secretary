package com.autosecretary.platform;

import static org.junit.Assert.assertEquals;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public final class AndroidLocationGatewayTest {
    @Test
    public void freshLocationIsStoredAndAvailableAfterPermissionIsRemoved() {
        Context context = ApplicationProvider.getApplicationContext();
        Application application = (Application) context;
        context.getSharedPreferences("last_location", Context.MODE_PRIVATE).edit().clear().commit();
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location location = new Location(LocationManager.NETWORK_PROVIDER);
        location.setLatitude(52.52);
        location.setLongitude(13.405);
        Shadows.shadowOf(manager).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, location);

        var fresh = new AndroidLocationGateway(context).lastKnown();
        Shadows.shadowOf(application).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        var saved = new AndroidLocationGateway(context).lastKnown();

        assertEquals(fresh, saved);
        assertEquals(52.52, saved.latitude(), 0.0001);
        assertEquals(13.405, saved.longitude(), 0.0001);
    }
}
