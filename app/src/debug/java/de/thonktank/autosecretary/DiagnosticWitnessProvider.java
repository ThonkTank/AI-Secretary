package de.thonktank.autosecretary;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.os.Process;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.work.BackoffPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/** Isolated native witness only; absent from the release manifest and APK. */
public final class DiagnosticWitnessProvider extends ContentProvider {
    @Override public boolean onCreate() {
        Context app = getContext().getApplicationContext();
        if (!"de.thonktank.autosecretary.test".equals(app.getPackageName())
                || !DiagnosticBootstrap.isDiagnosing()) return true;
        ((Application) app).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            private final java.util.Set<String> created = new java.util.HashSet<>();
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                created.add(activity.getClass().getName());
                activityProof(activity, false);
            }
            @Override public void onActivityDestroyed(Activity activity) { activityProof(activity, true); }
            private void activityProof(Activity activity, boolean destroyed) {
                proof(app, "diagnostic-activity-" + activity.getClass().getSimpleName() + ".json",
                        "activity", activity.getClass().getName(),
                        "created", created.contains(activity.getClass().getName()), "destroyed", destroyed,
                        "finishing", activity.isFinishing(), "diagnosing", DiagnosticBootstrap.isDiagnosing());
            }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
        });
        WorkManager manager = WorkManager.getInstance(app);
        proof(app, "diagnostic-provider-proof.json", "earlyMode", true,
                "containerMissing", AutoSecretaryApplication.from(app).container() == null,
                "workManagerInitialized", manager != null);
        ContextCompat.registerReceiver(app, new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                PendingResult pending = goAsync();
                ServiceConnection connection = new ServiceConnection() {
                    @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                        proof(app, "diagnostic-service-proof.json", "component", name.getClassName(),
                                "connected", binder != null);
                        app.unbindService(this);
                    }
                    @Override public void onServiceDisconnected(ComponentName name) { }
                };
                boolean bound = app.bindService(new Intent().setClassName(app,
                        "androidx.work.impl.background.systemjob.SystemJobService"), connection,
                        Context.BIND_AUTO_CREATE);
                if (!bound) proof(app, "diagnostic-service-proof.json", "error", "bind rejected");
                OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(FlowWakeWorker.class)
                        .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS).build();
                manager.enqueue(work);
                new Thread(() -> {
                    try {
                        long deadline = android.os.SystemClock.elapsedRealtime() + 10_000;
                        while (android.os.SystemClock.elapsedRealtime() < deadline) {
                            WorkInfo info = manager.getWorkInfoById(work.getId()).get(1, TimeUnit.SECONDS);
                            if (info != null && info.getState() == WorkInfo.State.ENQUEUED
                                    && info.getRunAttemptCount() > 0) {
                                proof(app, "diagnostic-worker-proof.json", "id", work.getId().toString(),
                                        "state", info.getState().name(), "attempts", info.getRunAttemptCount());
                                return;
                            }
                            Thread.sleep(50);
                        }
                        throw new AssertionError("Real FlowWakeWorker did not return retry");
                    } catch (Throwable failure) {
                        proof(app, "diagnostic-worker-proof.json", "error", failure.toString());
                    } finally { pending.finish(); }
                }, "diagnostic-worker-witness").start();
            }
        }, new IntentFilter("de.thonktank.autosecretary.test.DIAGNOSTIC_EVENTS"),
                ContextCompat.RECEIVER_EXPORTED);
        return true;
    }

    private static void proof(Context context, String name, Object... entries) {
        try {
            JSONObject result = new JSONObject().put("pid", Process.myPid());
            for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
            Files.write(new File(context.getFilesDir(), name).toPath(),
                    result.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception failure) { throw new IllegalStateException("Native diagnostic witness failed", failure); }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] arguments, String order) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] arguments) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] arguments) { return 0; }
}
