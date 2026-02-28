package com.autosecretary.app.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.autosecretary.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Self-update checker: fetches the latest version from GitHub and prompts the user to install updates.
 *
 * <h2>Threading Model</h2>
 * {@code checkForUpdate()} runs asynchronously on a background executor to avoid blocking app startup.
 * All UI callbacks post to the main thread via Handler. This ensures network I/O never blocks the main thread.
 *
 * <h2>Lifecycle Safety</h2>
 * Uses {@link WeakReference} to store the Activity reference. This prevents memory leaks if a background
 * download outlives the Activity (e.g., user rotates screen or closes app during download). Before each UI operation,
 * we check {@code activity.isFinishing()} and {@code activity.isDestroyed()} to ensure the Activity is still alive.
 *
 * <h2>Integration</h2>
 * Instantiate in {@code MainActivity.onCreate()} or via {@code AppCompositionRoot}. Example:
 * <pre>
 *   UpdateChecker checker = new UpdateChecker(activity, executor);
 *   checker.checkForUpdate();  // Runs on background thread; safe to call on main thread
 * </pre>
 *
 * <h2>External Dependencies</h2>
 * Fetches version from {@code ops/release/version.txt} and APK from {@code ops/release/AutoSecretary.apk}
 * on GitHub (raw content). Requires FileProvider configured in {@code AndroidManifest.xml}
 * with authority suffix {@code .fileprovider}.
 *
 * @see android.app.Activity
 * @see android.content.pm.PackageInfo
 * @see androidx.core.content.FileProvider
 */
public class UpdateChecker {

    private static final String LOG_TAG = "UpdateChecker";
    private static final String VERSION_URL =
            "https://raw.githubusercontent.com/ThonkTank/AI-Secretary/main/ops/release/version.txt";
    private static final String APK_URL =
            "https://github.com/ThonkTank/AI-Secretary/raw/main/ops/release/AutoSecretary.apk";
    private static final String FILE_PROVIDER_SUFFIX = ".fileprovider";
    private static final String APK_FILE_NAME = "update.apk";

    // Connect timeout for version check: 5s is adequate for a small text file on a stable network.
    private static final int VERSION_CONNECT_TIMEOUT_MS = 5000;
    // Read timeout for version check: 5s is adequate for a small text file.
    private static final int VERSION_READ_TIMEOUT_MS = 5000;
    // Connect timeout for APK download: 10s allows for initial server response.
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 10000;
    // Read timeout for APK download: 30s is conservative for a ~100MB file on a typical mobile network.
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 30000;

    /**
     * Weak reference to the Activity. Weak to prevent memory leaks if a background download outlives
     * the Activity lifecycle (e.g., user rotates screen or closes app during download).
     */
    private final WeakReference<Activity> activityRef;

    /**
     * Main thread handler. All UI operations (showing dialogs, starting intents) post via this handler
     * to ensure they run on the main/UI thread, even though the download and version check run on a background executor.
     */
    private final Handler mainHandler;

    /**
     * Background executor. All network I/O (version check, APK download) runs on this executor.
     * Typically a single-threaded executor from {@code AppCompositionRoot}.
     */
    private final ExecutorService backgroundExecutor;

    /**
     * Constructs an UpdateChecker.
     *
     * @param activity The Activity to use for showing dialogs and starting the install intent.
     *                 Stored as a WeakReference to prevent memory leaks.
     * @param executor A background executor for network I/O. Typically single-threaded.
     *                 Usually {@code AppCompositionRoot.executor()}.
     */
    public UpdateChecker(Activity activity, ExecutorService executor) {
        this.activityRef = new WeakReference<>(activity);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.backgroundExecutor = executor;
    }

    /**
     * Asynchronously checks for a newer version on GitHub.
     *
     * This method returns immediately. It spawns a background task that:
     * 1. Fetches the remote version from {@code ops/release/version.txt}
     * 2. Compares against the local version code
     * 3. If remote > local, shows an update dialog on the main thread
     *
     * Safe to call on the main thread. Network failures are logged but do not crash the app.
     * This is intentional: app startup must not be delayed or blocked by update checks.
     *
     * Safe to call multiple times; each call spawns an independent background task.
     */
    public void checkForUpdate() {
        backgroundExecutor.execute(() -> {
            try {
                Activity activity = getAliveActivity();
                if (activity == null) return;
                int remoteVersion = fetchRemoteVersion();
                // Read local version from PackageInfo
                int localVersion;
                try {
                    PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                    localVersion = (int) info.getLongVersionCode();
                } catch (Exception e) {
                    Log.w(LOG_TAG, "Failed to read local version", e);
                    localVersion = 0;
                }
                if (remoteVersion > localVersion) {
                    mainHandler.post(() -> showUpdateDialog(remoteVersion));
                }
            } catch (Exception e) {
                // Version check failures are not critical; app startup must not be blocked or delayed.
                Log.d(LOG_TAG, "Version check failed", e);
            }
        });
    }

    /**
     * Fetches the remote version number from {@code ops/release/version.txt} on GitHub.
     *
     * The file is expected to contain a single integer (the version code) on a single line.
     * Network timeouts are configured to handle mobile networks: 5s for both connect and read.
     *
     * @return The remote version code as an integer.
     * @throws IOException if the network request fails or returns a non-success status code.
     * @throws NumberFormatException if the remote file does not contain a valid integer.
     */
    private int fetchRemoteVersion() throws IOException, NumberFormatException {
        HttpURLConnection connection = (HttpURLConnection) new URL(VERSION_URL).openConnection();
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setConnectTimeout(VERSION_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(VERSION_READ_TIMEOUT_MS);

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error: " + responseCode);
            }
            try (InputStream inputStream = connection.getInputStream()) {
                String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
                return Integer.parseInt(text);
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Retrieves the Activity if it is still alive (not destroyed, not finishing).
     *
     * This is critical for preventing crashes when background tasks (version checks, downloads)
     * outlive the Activity lifecycle. For example:
     * - User closes app during download → Activity is destroyed → NPE if we try to show a dialog
     * - User rotates screen → Activity is recreated (and old ref becomes stale)
     *
     * @return The Activity, or null if it has been garbage collected, is destroying, or is destroyed.
     */
    private Activity getAliveActivity() {
        Activity activity = activityRef.get();
        // Check both isFinishing() and isDestroyed() to handle edge cases:
        // - isFinishing(): True if onDestroy() has been called but object may still exist.
        // - isDestroyed(): True if the Activity has fully completed its lifecycle.
        // Together, they ensure we don't try to show dialogs or start intents on a dead Activity.
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return null;
        }
        return activity;
    }

    /**
     * Shows a dialog offering to install a newer version.
     *
     * - Positive button ("Install"): calls {@link #downloadAndInstall()}
     * - Negative button ("Later"): dismisses the dialog
     *
     * @param newVersion The version code of the newer release (displayed in the message).
     */
    private void showUpdateDialog(int newVersion) {
        Activity activity = getAliveActivity();
        if (activity == null) return;

        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(activity.getString(R.string.update_available_message, newVersion))
                .setPositiveButton(R.string.update_action_install, (dialog, which) -> downloadAndInstall())
                .setNegativeButton(R.string.update_action_later, null)
                .show();
    }

    /**
     * Background task: downloads the APK from GitHub and initiates installation.
     *
     * Runs on the background executor. On success, posts the install intent to the main thread.
     * On failure, shows an error dialog on the main thread.
     */
    private void downloadAndInstall() {
        backgroundExecutor.execute(() -> {
            try {
                Activity activity = getAliveActivity();
                if (activity == null) return;
                File apkFile = downloadApk(activity);
                mainHandler.post(() -> installApk(apkFile));
            } catch (Exception e) {
                Log.e(LOG_TAG, "APK download/install failed", e);
                mainHandler.post(() -> showDownloadErrorDialog(e));
            }
        });
    }

    /**
     * Downloads the APK from {@code ops/release/AutoSecretary.apk} on GitHub.
     *
     * Streams the file to {@code cacheDir/update.apk} using a standard 8KB buffer.
     * Cache dir is used because the APK is temporary (installed once, then deleted).
     *
     * @param activity The Activity (used to get the cache directory).
     * @return A File pointing to the downloaded APK.
     * @throws IOException if the network request fails, returns a non-success status code, or file I/O fails.
     */
    private File downloadApk(Activity activity) throws IOException {
        File apkFile = new File(activity.getCacheDir(), APK_FILE_NAME);

        HttpURLConnection connection = (HttpURLConnection) new URL(APK_URL).openConnection();
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setConnectTimeout(DOWNLOAD_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error: " + responseCode);
            }
            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(apkFile, false)) {
                inputStream.transferTo(outputStream);
            }
        } finally {
            connection.disconnect();
        }

        return apkFile;
    }

    /**
     * Initiates APK installation via the system installer.
     *
     * Uses FileProvider to provide a content:// URI instead of file://. This is required
     * on Android 7.0+ (API 24+) due to scoped file access restrictions (StrictMode).
     *
     * The FileProvider authority is: {@code <package-name>.fileprovider}
     * This must be registered in AndroidManifest.xml with a <provider> element that declares
     * the paths where APK files can be accessed.
     *
     * @param apkFile The APK file to install.
     */
    private void installApk(File apkFile) {
        Activity activity = getAliveActivity();
        if (activity == null) return;

        try {
            // FileProvider converts file:// to content://, which respects Android 7.0+ scoped file access.
            // Authority format: <package-name>.fileprovider
            Uri uri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + FILE_PROVIDER_SUFFIX,
                    apkFile
            );

            // ACTION_VIEW with application/vnd.android.package-archive triggers the system installer.
            // FLAG_GRANT_READ_URI_PERMISSION: allows installer to read the content:// URI.
            // FLAG_ACTIVITY_NEW_TASK: required because we're starting from a service context (if Activity is gone).
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(LOG_TAG, "Failed to find app installer", e);
            showDownloadErrorDialog(e);
        }
    }

    /**
     * Shows an error dialog with the download/install failure details.
     *
     * @param error The exception that caused the failure. Its message is displayed to the user.
     */
    private void showDownloadErrorDialog(Exception error) {
        Activity activity = getAliveActivity();
        if (activity == null) return;

        String detail = Objects.requireNonNullElse(
                error.getMessage(),
                activity.getString(R.string.update_download_failed_unknown)
        );
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_download_failed_title)
                .setMessage(activity.getString(R.string.update_download_failed_message, detail))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

}
