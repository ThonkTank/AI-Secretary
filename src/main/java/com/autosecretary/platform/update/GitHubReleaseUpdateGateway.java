package com.autosecretary.platform.update;

import android.app.DownloadManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;

import com.autosecretary.BuildConfig;
import com.autosecretary.application.update.UpdateGateway;
import com.autosecretary.application.update.UpdateInfo;
import com.autosecretary.application.update.VerifiedUpdate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** GitHub production channel with size limits, pinned asset names and local APK verification. */
public final class GitHubReleaseUpdateGateway implements UpdateGateway {
    private static final String RELEASE_API =
            "https://api.github.com/repos/ThonkTank/AI-Secretary/releases/latest";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int METADATA_LIMIT = 1_000_000;
    private static final long APK_LIMIT = 800L * 1024L * 1024L;
    private static final long DOWNLOAD_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);

    private final Context context;

    public GitHubReleaseUpdateGateway(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public UpdateInfo check() {
        try {
            JSONObject release = new JSONObject(readText(RELEASE_API, METADATA_LIMIT));
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null;
            Map<String, String> assets = assetUrls(release.getJSONArray("assets"));
            String versionUrl = requiredAsset(assets, "version.txt");
            int remoteVersion = Integer.parseInt(readText(versionUrl, 64).trim());
            if (remoteVersion <= BuildConfig.VERSION_CODE) return null;
            String checksum = readText(requiredAsset(assets, "AutoSecretary.apk.sha256"), 256)
                    .trim().split("\\s+")[0];
            String tag = release.optString("tag_name", Integer.toString(remoteVersion));
            return new UpdateInfo(remoteVersion, tag.startsWith("v") ? tag.substring(1) : tag,
                    requiredAsset(assets, "AutoSecretary.apk"), checksum);
        } catch (Exception error) {
            throw new IllegalStateException("Update-Prüfung fehlgeschlagen", error);
        }
    }

    @Override
    public VerifiedUpdate downloadAndVerify(UpdateInfo update) {
        File external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (external == null) {
            throw new IllegalStateException("Externes Update-Verzeichnis ist nicht verfügbar");
        }
        File directory = new File(external, "updates");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Update-Verzeichnis konnte nicht angelegt werden");
        }
        File target = new File(directory, "AutoSecretary-" + update.versionCode() + ".apk");
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("Altes Update-Paket konnte nicht entfernt werden");
        }
        try {
            downloadWithSystemManager(update.apkUrl(), target);
            if (target.length() <= 0 || target.length() > APK_LIMIT) {
                throw new SecurityException("Update-Paket ist unerwartet groß");
            }
            verifyPackage(target, update);
            return new VerifiedUpdate(update, target);
        } catch (Exception error) {
            target.delete();
            throw new IllegalStateException("Update-Paket wurde verworfen", error);
        }
    }

    private void downloadWithSystemManager(String url, File target) throws Exception {
        Uri source = Uri.parse(url);
        if (!"https".equals(source.getScheme())) throw new SecurityException("Unsichere Update-URL");
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) throw new IllegalStateException("DownloadManager ist nicht verfügbar");
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
        request.addRequestHeader("User-Agent", "AutoSecretary/" + BuildConfig.VERSION_NAME);

        long downloadId = manager.enqueue(request);
        Semaphore changed = new Semaphore(0);
        ContentObserver observer = new ContentObserver(null) {
            @Override public void onChange(boolean selfChange) { changed.release(); }
        };
        context.getContentResolver().registerContentObserver(
                Uri.parse("content://downloads/my_downloads"), true, observer);
        long deadline = SystemClock.elapsedRealtime() + DOWNLOAD_TIMEOUT_MS;
        try {
            while (true) {
                DownloadState state = queryDownload(manager, downloadId);
                if (state.status() == DownloadManager.STATUS_SUCCESSFUL) {
                    if (!target.isFile()) throw new IllegalStateException("Download-Datei fehlt");
                    return;
                }
                if (state.status() == DownloadManager.STATUS_FAILED) {
                    throw new IllegalStateException("Systemdownload fehlgeschlagen: " + state.reason());
                }
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0) throw new IllegalStateException("Systemdownload hat Zeitlimit überschritten");
                changed.tryAcquire(Math.min(remaining, 30_000), TimeUnit.MILLISECONDS);
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            }
        } catch (InterruptedException error) {
            manager.remove(downloadId);
            Thread.currentThread().interrupt();
            throw error;
        } catch (Exception error) {
            manager.remove(downloadId);
            throw error;
        } finally {
            context.getContentResolver().unregisterContentObserver(observer);
        }
    }

    private static DownloadState queryDownload(DownloadManager manager, long id) {
        try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor == null || !cursor.moveToFirst()) return new DownloadState(0, 0);
            return new DownloadState(
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
        }
    }

    private record DownloadState(int status, int reason) { }

    private void verifyPackage(File apk, UpdateInfo update) throws Exception {
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo installed = manager.getPackageInfo(BuildConfig.APPLICATION_ID, flags);
        UpdatePackageVerifier.PackageEvidence installedEvidence = evidence(installed);
        UpdatePackageVerifier.PackageEvidence archiveEvidence = evidence(archive);
        UpdatePackageVerifier.verify(apk, update, BuildConfig.VERSION_CODE,
                BuildConfig.APPLICATION_ID, installedEvidence, archiveEvidence);
    }

    private static UpdatePackageVerifier.PackageEvidence evidence(PackageInfo info) throws Exception {
        if (info == null) return null;
        long version = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode() : info.versionCode;
        return new UpdatePackageVerifier.PackageEvidence(
                info.packageName, version, certificateDigests(info));
    }

    private static Set<String> certificateDigests(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (info.signingInfo == null) return Set.of();
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        Set<String> result = new HashSet<>();
        if (signatures == null) return result;
        for (Signature signature : signatures) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            result.add(hex(digest.digest(signature.toByteArray())));
        }
        return result;
    }

    private static Map<String, String> assetUrls(JSONArray source) throws Exception {
        Map<String, String> result = new HashMap<>();
        for (int index = 0; index < source.length(); index++) {
            JSONObject asset = source.getJSONObject(index);
            result.put(asset.getString("name"), asset.getString("browser_download_url"));
        }
        return result;
    }

    private static String requiredAsset(Map<String, String> assets, String name) {
        String value = assets.get(name);
        if (value == null || !value.startsWith("https://github.com/ThonkTank/AI-Secretary/")) {
            throw new IllegalStateException("Produktions-Asset fehlt: " + name);
        }
        return value;
    }

    private static String readText(String url, int limit) throws Exception {
        try (InputStream input = open(url); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > limit) throw new IllegalStateException("Update-Metadaten sind zu groß");
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static InputStream open(String url) throws Exception {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme())) throw new SecurityException("Unsichere Update-URL");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "AutoSecretary/" + BuildConfig.VERSION_NAME);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IllegalStateException("Update-Server antwortet mit " + status);
        }
        return new java.io.FilterInputStream(connection.getInputStream()) {
            @Override public void close() throws java.io.IOException {
                try { super.close(); } finally { connection.disconnect(); }
            }
        };
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte part : value) result.append(String.format(Locale.ROOT, "%02x", part));
        return result.toString();
    }
}
