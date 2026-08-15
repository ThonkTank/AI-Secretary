package com.autosecretary.platform.update;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.autosecretary.application.update.DownloadProgress;
import com.autosecretary.application.update.DownloadTicket;
import com.autosecretary.application.update.UpdateFailure;
import com.autosecretary.application.update.UpdateInfo;

import java.io.File;

/** Thin, non-blocking DownloadManager adapter. Ownership survives process death. */
final class DownloadManagerUpdateDownloader {
    private static final String PREFERENCES = "phone_update_download";
    private static final String DOWNLOAD_ID = "download_id";
    private static final String DOWNLOAD_VERSION = "version_code";
    private final Context context;
    private final String appVersionName;

    DownloadManagerUpdateDownloader(Context context, String appVersionName) {
        this.context = context.getApplicationContext();
        this.appVersionName = appVersionName;
    }

    DownloadTicket enqueue(UpdateInfo update, File target) {
        Uri source = Uri.parse(update.apkUrl());
        if (!"https".equals(source.getScheme())) throw new SecurityException("Unsichere Update-URL");
        DownloadManager manager = manager();
        var preferences = preferences();
        long existingId = preferences.getInt(DOWNLOAD_VERSION, 0) == update.versionCode()
                ? preferences.getLong(DOWNLOAD_ID, 0) : 0;
        if (existingId > 0) {
            DownloadTicket existing = new DownloadTicket(existingId, update.versionCode());
            if (!(query(existing) instanceof DownloadProgress.Failed)) return existing;
            manager.remove(existingId);
            preferences.edit().clear().commit();
        }
        long obsolete = preferences.getLong(DOWNLOAD_ID, 0);
        if (obsolete > 0) manager.remove(obsolete);
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("Altes Update-Paket konnte nicht entfernt werden");
        }
        DownloadManager.Request request = new DownloadManager.Request(source)
                .setTitle("Auto Secretary Update")
                .setDescription("Signiertes Update wird geladen")
                .setMimeType("application/vnd.android.package-archive")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(target));
        request.addRequestHeader("Accept", "application/vnd.android.package-archive");
        request.addRequestHeader("User-Agent", "AutoSecretary/" + appVersionName);
        long id = manager.enqueue(request);
        if (!preferences.edit().putLong(DOWNLOAD_ID, id)
                .putInt(DOWNLOAD_VERSION, update.versionCode()).commit()) {
            manager.remove(id);
            throw new IllegalStateException("Systemdownload konnte nicht gespeichert werden");
        }
        return new DownloadTicket(id, update.versionCode());
    }

    DownloadProgress query(DownloadTicket ticket) {
        if (!owns(ticket)) {
            return failed("Download-ID ist verschwunden oder gehört nicht zu diesem Update", true);
        }
        try (Cursor cursor = manager().query(
                new DownloadManager.Query().setFilterById(ticket.id()))) {
            if (cursor == null || !cursor.moveToFirst()) {
                return failed("Systemdownload ist verschwunden", true);
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_STATUS));
            long downloaded = Math.max(0, cursor.getLong(cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            return switch (status) {
                case DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED ->
                        new DownloadProgress.Pending();
                case DownloadManager.STATUS_RUNNING ->
                        new DownloadProgress.Running(downloaded, total);
                case DownloadManager.STATUS_SUCCESSFUL -> new DownloadProgress.Complete();
                case DownloadManager.STATUS_FAILED -> failureForReason(cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
                default -> failed("Unbekannter Systemdownload-Zustand: " + status, true);
            };
        }
    }

    void cancel(DownloadTicket ticket) {
        if (owns(ticket)) manager().remove(ticket.id());
        preferences().edit().clear().commit();
    }

    void forget(DownloadTicket ticket) {
        if (owns(ticket)) preferences().edit().clear().commit();
    }

    private boolean owns(DownloadTicket ticket) {
        return preferences().getLong(DOWNLOAD_ID, 0) == ticket.id()
                && preferences().getInt(DOWNLOAD_VERSION, 0) == ticket.versionCode();
    }

    private DownloadManager manager() {
        DownloadManager manager = context.getSystemService(DownloadManager.class);
        if (manager == null) throw new IllegalStateException("DownloadManager ist nicht verfügbar");
        return manager;
    }

    private android.content.SharedPreferences preferences() {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static DownloadProgress.Failed failed(String detail, boolean retryable) {
        return new DownloadProgress.Failed(new UpdateFailure(
                UpdateFailure.Kind.DOWNLOAD_FAILED, detail, retryable));
    }

    private static DownloadProgress.Failed failureForReason(int reason) {
        UpdateFailure.Kind kind = reason == DownloadManager.ERROR_INSUFFICIENT_SPACE
                ? UpdateFailure.Kind.STORAGE : UpdateFailure.Kind.DOWNLOAD_FAILED;
        return new DownloadProgress.Failed(new UpdateFailure(
                kind, "Systemdownload fehlgeschlagen: " + reason, true));
    }
}
