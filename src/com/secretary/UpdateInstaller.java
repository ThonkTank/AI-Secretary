package com.secretary.helloworld;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import java.io.File;

public class UpdateInstaller {
    private static final String TAG = "UpdateInstaller";

    public static void downloadAndInstall(Context context, String downloadUrl, String version) {
        AppLogger logger = AppLogger.getInstance(context);
        try {
            logger.info(TAG, "Starting download for version " + version);
            logger.debug(TAG, "Download URL: " + downloadUrl);

            // Download-Manager verwenden
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

            Uri uri = Uri.parse(downloadUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("AI Secretary Update");
            request.setDescription("Downloading version " + version);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AISecretary-" + version + ".apk");

            long downloadId = downloadManager.enqueue(request);
            logger.info(TAG, "Download enqueued with ID: " + downloadId);

            // Use Handler to poll download status instead of BroadcastReceiver
            // This is more reliable on newer Android versions
            Handler handler = new Handler(Looper.getMainLooper());
            Runnable checkDownload = new Runnable() {
                @Override
                public void run() {
                    AppLogger logger = AppLogger.getInstance(context);

                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    Cursor cursor = downloadManager.query(query);

                    if (cursor != null && cursor.moveToFirst()) {
                        int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int status = cursor.getInt(statusIndex);

                        switch (status) {
                            case DownloadManager.STATUS_SUCCESSFUL:
                                logger.info(TAG, "Download completed successfully");

                                // Get download URI from DownloadManager (content:// URI, not file://)
                                Uri downloadUri = downloadManager.getUriForDownloadedFile(downloadId);

                                if (downloadUri != null) {
                                    logger.info(TAG, "Starting installation with URI: " + downloadUri.toString());
                                    Toast.makeText(context, "Download complete! Starting installation...", Toast.LENGTH_LONG).show();

                                    cursor.close();

                                    // Install APK using content URI
                                    installApk(context, downloadUri);
                                } else {
                                    logger.error(TAG, "Failed to get download URI");
                                    Toast.makeText(context, "Installation failed. Please install manually from Downloads.", Toast.LENGTH_LONG).show();
                                    cursor.close();
                                }
                                break;

                            case DownloadManager.STATUS_FAILED:
                                logger.error(TAG, "Download failed");
                                Toast.makeText(context, "Download failed. Please try again.", Toast.LENGTH_LONG).show();
                                cursor.close();
                                break;

                            case DownloadManager.STATUS_RUNNING:
                                // Get download progress
                                int bytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                                int totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                                int bytes = cursor.getInt(bytesIndex);
                                int total = cursor.getInt(totalIndex);

                                if (total > 0) {
                                    int progress = (bytes * 100) / total;
                                    logger.debug(TAG, "Download progress: " + progress + "%");
                                }

                                cursor.close();
                                // Check again in 500ms
                                handler.postDelayed(this, 500);
                                break;

                            case DownloadManager.STATUS_PENDING:
                            case DownloadManager.STATUS_PAUSED:
                                logger.debug(TAG, "Download status: " + status);
                                cursor.close();
                                // Check again in 500ms
                                handler.postDelayed(this, 500);
                                break;
                        }
                    } else {
                        if (cursor != null) cursor.close();
                        logger.error(TAG, "Download cursor is null or empty");
                    }
                }
            };

            logger.info(TAG, "Starting download status monitoring");
            // Start checking download status after 100ms
            handler.postDelayed(checkDownload, 100);

        } catch (Exception e) {
            logger.error(TAG, "Error downloading update", e);
        }
    }

    private static void installApk(Context context, Uri apkUri) {
        AppLogger logger = AppLogger.getInstance(context);
        try {
            logger.info(TAG, "Launching APK installer with URI: " + apkUri.toString());

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // For Android 7+ (API 24+), must use content:// URIs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // URI should already be a content:// URI from DownloadManager
                logger.info(TAG, "Using content URI for Android 7+");
            }

            context.startActivity(intent);
            logger.info(TAG, "APK installer intent launched successfully");
            Toast.makeText(context, "Opening installer...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            logger.error(TAG, "Error installing APK", e);
            Toast.makeText(context, "Failed to open installer. Please install manually from Downloads.", Toast.LENGTH_LONG).show();
        }
    }
}
