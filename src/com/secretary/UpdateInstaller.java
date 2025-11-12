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

                                // Get APK URI
                                String uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                                Uri apkUri = Uri.parse(uriString);

                                logger.info(TAG, "Starting installation for: " + uriString);
                                Toast.makeText(context, "Download complete! Starting installation...", Toast.LENGTH_LONG).show();

                                cursor.close();

                                // Install APK
                                installApk(context, apkUri);
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
            logger.info(TAG, "Launching APK installer intent");
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
            logger.info(TAG, "APK installer intent launched successfully");
        } catch (Exception e) {
            logger.error(TAG, "Error installing APK", e);
        }
    }
}
