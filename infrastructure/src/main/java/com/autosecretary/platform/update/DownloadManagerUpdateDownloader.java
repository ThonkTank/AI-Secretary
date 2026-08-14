package com.autosecretary.platform.update;

import android.app.DownloadManager;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;

import com.autosecretary.application.update.UpdateInfo;

import java.io.File;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Resumable system-owned APK transfer. The ticket survives process death in preferences. */
final class DownloadManagerUpdateDownloader {
    private static final String PREFERENCES = "phone_update_download";
    private static final String DOWNLOAD_ID = "download_id";
    private static final String DOWNLOAD_VERSION = "version_code";
    private static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);
    private final Context context;
    private final String appVersionName;

    DownloadManagerUpdateDownloader(Context context, String appVersionName) {
        this.context = context.getApplicationContext();
        this.appVersionName = appVersionName;
    }

    void download(UpdateInfo update, File target) throws Exception {
        Uri source = Uri.parse(update.apkUrl());
        if (!"https".equals(source.getScheme())) throw new SecurityException("Unsichere Update-URL");
        DownloadManager manager = context.getSystemService(DownloadManager.class);
        if (manager == null) throw new IllegalStateException("DownloadManager ist nicht verfügbar");
        var preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        long id = preferences.getInt(DOWNLOAD_VERSION, 0) == update.versionCode()
                ? preferences.getLong(DOWNLOAD_ID, 0) : 0;
        if (id < 1) {
            long obsolete = preferences.getLong(DOWNLOAD_ID, 0);
            if (obsolete > 0) manager.remove(obsolete);
            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("Altes Update-Paket konnte nicht entfernt werden");
            }
            DownloadManager.Request request = new DownloadManager.Request(source)
                    .setTitle("Auto Secretary Update")
                    .setDescription("Signiertes Update wird geladen")
                    .setMimeType("application/vnd.android.package-archive")
                    .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                    .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationUri(Uri.fromFile(target));
            request.addRequestHeader("Accept", "application/vnd.android.package-archive");
            request.addRequestHeader("User-Agent", "AutoSecretary/" + appVersionName);
            id = manager.enqueue(request);
            if (!preferences.edit().putLong(DOWNLOAD_ID, id)
                    .putInt(DOWNLOAD_VERSION, update.versionCode()).commit()) {
                manager.remove(id);
                throw new IllegalStateException("Systemdownload konnte nicht gespeichert werden");
            }
        }

        long downloadId = id;
        Semaphore changed = new Semaphore(0);
        ContentObserver observer = new ContentObserver(null) {
            @Override public void onChange(boolean selfChange) { changed.release(); }
        };
        context.getContentResolver().registerContentObserver(
                Uri.parse("content://downloads/my_downloads"), true, observer);
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        try {
            while (true) {
                DownloadState state = query(manager, downloadId);
                if (state.status() == DownloadManager.STATUS_SUCCESSFUL) {
                    if (!target.isFile()) throw new IllegalStateException("Download-Datei fehlt");
                    preferences.edit().clear().commit();
                    return;
                }
                if (state.status() == DownloadManager.STATUS_FAILED || state.status() == 0) {
                    throw new IllegalStateException("Systemdownload fehlgeschlagen: " + state.reason());
                }
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0) throw new IllegalStateException("Systemdownload-Timeout");
                changed.tryAcquire(Math.min(remaining, 30_000), TimeUnit.MILLISECONDS);
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            }
        } catch (InterruptedException error) {
            manager.remove(downloadId);
            preferences.edit().clear().commit();
            Thread.currentThread().interrupt();
            throw error;
        } catch (Exception error) {
            manager.remove(downloadId);
            preferences.edit().clear().commit();
            throw error;
        } finally {
            context.getContentResolver().unregisterContentObserver(observer);
        }
    }

    private static DownloadState query(DownloadManager manager, long id) {
        try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor == null || !cursor.moveToFirst()) return new DownloadState(0, 0);
            return new DownloadState(
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
        }
    }

    private record DownloadState(int status, int reason) { }
}
