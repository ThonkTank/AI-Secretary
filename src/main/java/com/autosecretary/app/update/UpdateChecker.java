package com.autosecretary.app.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateChecker {

    private static final String VERSION_URL =
            "https://raw.githubusercontent.com/ThonkTank/AI-Secretary/main/ops/release/version.txt";
    private static final String APK_URL =
            "https://github.com/ThonkTank/AI-Secretary/raw/main/ops/release/AutoSecretary.apk";

    private static final int VERSION_CONNECT_TIMEOUT_MS = 5000;
    private static final int VERSION_READ_TIMEOUT_MS = 5000;
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 10000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 30000;

    private final Activity activity;
    private final Handler mainHandler;
    private final ExecutorService backgroundExecutor;

    public UpdateChecker(Activity activity) {
        this.activity = activity;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.backgroundExecutor = Executors.newSingleThreadExecutor();
    }

    public void checkForUpdate() {
        backgroundExecutor.execute(() -> {
            try {
                int remoteVersion = fetchRemoteVersion();
                int localVersion = getLocalVersion();
                if (remoteVersion > localVersion) {
                    postToUi(() -> showUpdateDialog(remoteVersion));
                }
            } catch (Exception ignored) {
                // Fehler beim Prüfen sind nicht kritisch; App-Start darf nicht blockieren.
            }
        });
    }

    private int fetchRemoteVersion() throws IOException, NumberFormatException {
        HttpURLConnection connection = (HttpURLConnection) new URL(VERSION_URL).openConnection();
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setConnectTimeout(VERSION_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(VERSION_READ_TIMEOUT_MS);

        try (InputStream inputStream = connection.getInputStream()) {
            String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            return Integer.parseInt(text);
        } finally {
            connection.disconnect();
        }
    }

    private int getLocalVersion() {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return (int) info.getLongVersionCode();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void showUpdateDialog(int newVersion) {
        if (!canInteractWithUi()) {
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle("Update verfügbar")
                .setMessage("Version " + newVersion + " ist verfügbar. Jetzt herunterladen?")
                .setPositiveButton("Update", (dialog, which) -> downloadAndInstall())
                .setNegativeButton("Später", null)
                .show();
    }

    private void downloadAndInstall() {
        backgroundExecutor.execute(() -> {
            try {
                File apkFile = downloadApk();
                postToUi(() -> installApk(apkFile));
            } catch (Exception e) {
                postToUi(() -> showDownloadErrorDialog(e));
            }
        });
    }

    private File downloadApk() throws IOException {
        File apkFile = new File(activity.getCacheDir(), "update.apk");

        HttpURLConnection connection = (HttpURLConnection) new URL(APK_URL).openConnection();
        connection.setConnectTimeout(DOWNLOAD_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);

        try (InputStream inputStream = connection.getInputStream();
             FileOutputStream outputStream = new FileOutputStream(apkFile, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }

        return apkFile;
    }

    private void installApk(File apkFile) {
        if (!canInteractWithUi()) {
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    apkFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            showDownloadErrorDialog(e);
        }
    }

    private void showDownloadErrorDialog(Exception error) {
        if (!canInteractWithUi()) {
            return;
        }

        String detail = error.getMessage() == null ? "Unbekannter Fehler" : error.getMessage();
        new AlertDialog.Builder(activity)
                .setTitle("Download fehlgeschlagen")
                .setMessage("Das Update konnte nicht geladen oder installiert werden.\n\n" + detail)
                .setPositiveButton("OK", null)
                .show();
    }

    private void postToUi(Runnable action) {
        mainHandler.post(action);
    }

    private boolean canInteractWithUi() {
        return !activity.isFinishing() && !activity.isDestroyed();
    }
}
