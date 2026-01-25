package controller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

/**
 * Prüft auf GitHub ob eine neuere APK-Version vorliegt und bietet Update an.
 */
public class updateChecker {

    private static final String VERSION_URL =
        "https://raw.githubusercontent.com/ThonkTank/AI-Secretary/main/release/version.txt";
    private static final String APK_URL =
        "https://github.com/ThonkTank/AI-Secretary/raw/main/release/AutoSecretary.apk";

    private final Activity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable onDone;

    public updateChecker(Activity activity) {
        this.activity = activity;
    }

    /** Prüft im Hintergrund auf Updates, zeigt Dialog falls verfügbar. Ruft onDone auf wenn fertig. */
    public void checkForUpdate(Runnable onDone) {
        this.onDone = onDone;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                int remoteVersion = fetchRemoteVersion();
                int localVersion = getLocalVersion();

                if (remoteVersion > localVersion) {
                    mainHandler.post(() -> showUpdateDialog(remoteVersion));
                } else {
                    mainHandler.post(this::done);
                }
            } catch (Exception ignored) {
                mainHandler.post(this::done);
            }
        });
    }

    private void done() {
        if (onDone != null) onDone.run();
    }

    private int fetchRemoteVersion() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(VERSION_URL).openConnection();
        conn.setUseCaches(false);
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        try (InputStream in = conn.getInputStream()) {
            String text = new String(in.readAllBytes()).trim();
            return Integer.parseInt(text);
        } finally {
            conn.disconnect();
        }
    }

    private int getLocalVersion() {
        try {
            PackageInfo info = activity.getPackageManager()
                .getPackageInfo(activity.getPackageName(), 0);
            return (int) info.getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    private void showUpdateDialog(int newVersion) {
        new AlertDialog.Builder(activity)
            .setTitle("Update verfügbar")
            .setMessage("Version " + newVersion + " ist verfügbar. Jetzt herunterladen?")
            .setPositiveButton("Update", (d, w) -> downloadAndInstall())
            .setNegativeButton("Später", (d, w) -> done())
            .setCancelable(false)
            .show();
    }

    private void downloadAndInstall() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File apkFile = new File(activity.getCacheDir(), "update.apk");
                HttpURLConnection conn = (HttpURLConnection) new URL(APK_URL).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(apkFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                } finally {
                    conn.disconnect();
                }

                mainHandler.post(() -> installApk(apkFile));
            } catch (Exception e) {
                mainHandler.post(() ->
                    new AlertDialog.Builder(activity)
                        .setTitle("Fehler")
                        .setMessage("Download fehlgeschlagen: " + e.getMessage())
                        .setPositiveButton("OK", null)
                        .show()
                );
            }
        });
    }

    private void installApk(File apkFile) {
        Uri uri = FileProvider.getUriForFile(activity,
            activity.getPackageName() + ".fileprovider", apkFile);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(intent);
    }
}
