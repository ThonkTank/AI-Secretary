package com.autosecretary.platform;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.autosecretary.application.LocationPort;

import java.util.function.Consumer;

public final class AndroidLocationGateway implements LocationPort {
    private final Context context;
    private final LocationManager manager;
    private Consumer<Position> consumer;
    private final LocationListener listener = location -> deliver(location);
    private boolean observing;

    public AndroidLocationGateway(Context context) {
        this.context = context.getApplicationContext();
        manager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public Position lastKnown() {
        if (!permitted() || manager == null) return null;
        try {
            Location location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (location == null) location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            return location == null ? null : position(location);
        } catch (SecurityException ignored) {
            return null;
        }
    }

    @Override
    public synchronized void start(Consumer<Position> listener) {
        stop();
        consumer = listener;
        if (!permitted() || manager == null) return;
        String currentProvider = null;
        try {
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    && requestProvider(LocationManager.NETWORK_PROVIDER)) {
                observing = true;
                currentProvider = LocationManager.NETWORK_PROVIDER;
            }
        } catch (RuntimeException ignored) { }
        try {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    && requestProvider(LocationManager.GPS_PROVIDER)) {
                observing = true;
                if (currentProvider == null) currentProvider = LocationManager.GPS_PROVIDER;
            }
        } catch (RuntimeException ignored) { }
        try {
            if (Build.VERSION.SDK_INT >= 30 && currentProvider != null) {
                requestCurrentLocation(currentProvider);
            }
        } catch (RuntimeException ignored) { }
    }

    @Override
    public synchronized void stop() {
        if (observing && manager != null) {
            try { manager.removeUpdates(listener); }
            catch (SecurityException ignored) { }
        }
        observing = false;
        consumer = null;
    }

    private void deliver(Location location) {
        Consumer<Position> target = consumer;
        if (target != null && location != null) target.accept(position(location));
    }

    private boolean permitted() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean requestProvider(String provider) {
        try {
            manager.requestLocationUpdates(provider, 15 * 60_000L, 5_000f,
                    listener, context.getMainLooper());
            return true;
        } catch (SecurityException | IllegalArgumentException error) {
            return false;
        }
    }

    /** Called only after the explicit runtime-permission check in {@link #start(Consumer)}. */
    @SuppressLint("MissingPermission")
    @RequiresApi(30)
    private void requestCurrentLocation(String provider) {
        manager.getCurrentLocation(provider, null, context.getMainExecutor(), this::deliver);
    }

    private static Position position(Location location) {
        return new Position(location.getLatitude(), location.getLongitude());
    }
}
