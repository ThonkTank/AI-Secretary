package com.secretary.helloworld;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final String GITHUB_REPO = "ThonkTank/AI-Secretary";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private static final String GITHUB_TOKEN = "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";

    public interface UpdateListener {
        void onUpdateAvailable(String version, String downloadUrl, String changelog);
        void onNoUpdateAvailable();
        void onError(String error);
    }

    public static void checkForUpdates(Context context, UpdateListener listener) {
        new Thread(() -> {
            try {
                // Aktuelle Version ermitteln
                int currentVersionCode = getCurrentVersionCode(context);
                String currentVersionName = getCurrentVersionName(context);

                Log.d(TAG, "Current version: " + currentVersionName + " (code: " + currentVersionCode + ")");

                // GitHub Releases API abfragen
                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("Authorization", "Bearer " + GITHUB_TOKEN);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    // JSON parsen
                    JSONObject release = new JSONObject(response.toString());
                    String latestVersionName = release.getString("tag_name").replace("v", "");
                    String changelog = release.optString("body", "Keine Changelog-Information verfügbar");

                    // APK Asset finden
                    String downloadUrl = null;
                    if (release.has("assets")) {
                        var assets = release.getJSONArray("assets");
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.getString("name");
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.getString("browser_download_url");
                                break;
                            }
                        }
                    }

                    // Version vergleichen
                    if (downloadUrl != null && !latestVersionName.equals(currentVersionName)) {
                        Log.d(TAG, "Update available: " + latestVersionName);
                        String finalDownloadUrl = downloadUrl;
                        String finalChangelog = changelog;
                        ((android.app.Activity) context).runOnUiThread(() ->
                            listener.onUpdateAvailable(latestVersionName, finalDownloadUrl, finalChangelog)
                        );
                    } else {
                        Log.d(TAG, "No update available");
                        ((android.app.Activity) context).runOnUiThread(() ->
                            listener.onNoUpdateAvailable()
                        );
                    }
                } else {
                    String error = "GitHub API returned " + responseCode;
                    Log.e(TAG, error);
                    ((android.app.Activity) context).runOnUiThread(() ->
                        listener.onError(error)
                    );
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates", e);
                ((android.app.Activity) context).runOnUiThread(() ->
                    listener.onError(e.getMessage())
                );
            }
        }).start();
    }

    private static int getCurrentVersionCode(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    private static String getCurrentVersionName(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }
}
