package com.autosecretary.app.update;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class GitHubReleaseUpdateClient implements UpdateClient {
    static final String APK_ASSET_NAME = "AutoSecretary.apk";
    static final String VERSION_ASSET_NAME = "version.txt";

    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/ThonkTank/AI-Secretary/releases/latest";
    private static final String USER_AGENT = "AutoSecretary";
    private static final String API_VERSION = "2022-11-28";
    private static final int RELEASE_CONNECT_TIMEOUT_MS = 5000;
    private static final int RELEASE_READ_TIMEOUT_MS = 5000;
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 10000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 30000;

    private final String latestReleaseUrl;
    private final int releaseConnectTimeoutMs;
    private final int releaseReadTimeoutMs;
    private final int downloadConnectTimeoutMs;
    private final int downloadReadTimeoutMs;

    GitHubReleaseUpdateClient() {
        this(
                LATEST_RELEASE_URL,
                RELEASE_CONNECT_TIMEOUT_MS,
                RELEASE_READ_TIMEOUT_MS,
                DOWNLOAD_CONNECT_TIMEOUT_MS,
                DOWNLOAD_READ_TIMEOUT_MS);
    }

    GitHubReleaseUpdateClient(
            String latestReleaseUrl,
            int releaseConnectTimeoutMs,
            int releaseReadTimeoutMs,
            int downloadConnectTimeoutMs,
            int downloadReadTimeoutMs) {
        this.latestReleaseUrl = latestReleaseUrl;
        this.releaseConnectTimeoutMs = releaseConnectTimeoutMs;
        this.releaseReadTimeoutMs = releaseReadTimeoutMs;
        this.downloadConnectTimeoutMs = downloadConnectTimeoutMs;
        this.downloadReadTimeoutMs = downloadReadTimeoutMs;
    }

    @Override
    public AvailableUpdate fetchLatestUpdate() throws IOException {
        try {
            JSONObject release = new JSONObject(fetchText(latestReleaseUrl,
                    releaseConnectTimeoutMs, releaseReadTimeoutMs));
            JSONArray assets = release.getJSONArray("assets");
            String versionUrl = requireAssetDownloadUrl(assets, VERSION_ASSET_NAME);
            String apkUrl = requireAssetDownloadUrl(assets, APK_ASSET_NAME);
            String versionText = fetchText(versionUrl, releaseConnectTimeoutMs, releaseReadTimeoutMs).trim();
            int versionCode = parseVersionCode(versionText);
            return new AvailableUpdate(
                    versionCode,
                    apkUrl,
                    release.optString("name", release.optString("tag_name", "")),
                    release.optString("html_url", ""));
        } catch (JSONException e) {
            throw new IOException("GitHub release response is not valid JSON", e);
        }
    }

    @Override
    public File downloadApk(AvailableUpdate update, File targetFile) throws IOException {
        HttpURLConnection connection = openConnection(
                update.apkDownloadUrl(),
                downloadConnectTimeoutMs,
                downloadReadTimeoutMs);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("APK download failed with HTTP " + responseCode);
            }
            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(targetFile, false)) {
                inputStream.transferTo(outputStream);
            }
            return targetFile;
        } finally {
            connection.disconnect();
        }
    }

    private String fetchText(String url, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        HttpURLConnection connection = openConnection(url, connectTimeoutMs, readTimeoutMs);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException(url + " failed with HTTP " + responseCode);
            }
            try (InputStream inputStream = connection.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(
            String url,
            int connectTimeoutMs,
            int readTimeoutMs) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setUseCaches(false);
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", API_VERSION);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        return connection;
    }

    private String requireAssetDownloadUrl(JSONArray assets, String assetName) throws JSONException, IOException {
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (assetName.equals(asset.optString("name"))) {
                String downloadUrl = asset.optString("browser_download_url", "");
                if (downloadUrl.isBlank()) {
                    throw new IOException("Release asset has no download URL: " + assetName);
                }
                return downloadUrl;
            }
        }
        throw new IOException("Release asset missing: " + assetName);
    }

    private int parseVersionCode(String versionText) throws IOException {
        try {
            return Integer.parseInt(versionText);
        } catch (NumberFormatException e) {
            throw new IOException("Release version.txt is not a valid versionCode: " + versionText, e);
        }
    }
}
