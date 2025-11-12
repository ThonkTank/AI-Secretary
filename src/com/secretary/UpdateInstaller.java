package com.secretary.helloworld;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
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

            // BroadcastReceiver für Download-Abschluss
            BroadcastReceiver onComplete = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    AppLogger logger = AppLogger.getInstance(context);
                    logger.info(TAG, "BroadcastReceiver triggered - ACTION_DOWNLOAD_COMPLETE");
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    logger.info(TAG, "Received download ID: " + id + " (expected: " + downloadId + ")");
                    if (id == downloadId) {
                        logger.info(TAG, "Download completed for ID: " + id);

                        // APK URI ermitteln
                        DownloadManager.Query query = new DownloadManager.Query();
                        query.setFilterById(downloadId);
                        Cursor cursor = downloadManager.query(query);

                        if (cursor.moveToFirst()) {
                            int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(columnIndex)) {
                                String uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                                Uri apkUri = Uri.parse(uriString);

                                logger.info(TAG, "Starting installation for: " + uriString);
                                Toast.makeText(context, "Download complete! Starting installation...", Toast.LENGTH_LONG).show();
                                // Installation starten
                                installApk(context, apkUri);
                            } else {
                                logger.error(TAG, "Download failed with status: " + cursor.getInt(columnIndex));
                                Toast.makeText(context, "Download failed. Please try again.", Toast.LENGTH_LONG).show();
                            }
                        }
                        cursor.close();
                        context.getApplicationContext().unregisterReceiver(this);
                    }
                }
            };

            // Use application context to survive dialog dismissal
            context.getApplicationContext().registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

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
