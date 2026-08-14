package com.autosecretary.platform.update;

import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;

import com.autosecretary.BuildConfig;
import com.autosecretary.application.update.UpdateInfo;
import com.autosecretary.application.update.VerifiedUpdate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
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

/** The single GitHub release lookup, download and verification path used by the app. */
public final class GitHubReleaseUpdater {
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/ThonkTank/AI-Secretary/releases/latest";
    private static final String METADATA_ASSET = "release-metadata.json";
    private static final String APK_ASSET = "AutoSecretary.apk";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int METADATA_LIMIT = 1_000_000;
    private static final long APK_LIMIT = 800L * 1024L * 1024L;
    private static final long DOWNLOAD_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);

    private final Context context;

    public GitHubReleaseUpdater(Context context) {
        this.context = context.getApplicationContext();
    }

    public UpdateInfo check() {
        try {
            PackageEvidence installed = installedPackage();
            JSONObject release = new JSONObject(
                    readText(LATEST_RELEASE_API, METADATA_LIMIT));
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                throw new SecurityException("Das neueste Update ist nicht regulär veröffentlicht");
            }
            Map<String, String> assets = assetUrls(release.getJSONArray("assets"));
            String metadataUrl = requiredAsset(assets, METADATA_ASSET);
            JSONObject metadata = new JSONObject(readText(metadataUrl, 16_384));
            String apkName = metadata.getString("apkAsset");
            if (!APK_ASSET.equals(apkName)) {
                throw new SecurityException("Update-Metadaten nennen ein unbekanntes APK");
            }
            ReleaseMetadata latest = ReleaseMetadata.from(
                    metadata, requiredAsset(assets, apkName));
            return selectLatestUpdate(release.getString("tag_name"), latest,
                    installed.versionCode(), BuildConfig.APPLICATION_ID, installed.signers());
        } catch (Exception error) {
            throw new IllegalStateException("Update-Prüfung fehlgeschlagen", error);
        }
    }

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

    static void validateLatest(
            String tag,
            ReleaseMetadata release,
            String expectedPackage,
            Set<String> installedSigners) {
        if (!("android-" + release.versionCode()).equals(tag)) {
            throw new SecurityException("Release-Tag und Android-Version widersprechen sich");
        }
        if (!expectedPackage.equals(release.packageName())) {
            throw new SecurityException("Freigabe gehört nicht zu dieser App");
        }
        if (!installedSigners.contains(release.signerSha256())) {
            throw new SecurityException("Freigabe verwendet nicht die installierte Signatur");
        }
    }

    static UpdateInfo selectLatestUpdate(
            String tag,
            ReleaseMetadata release,
            long installedVersion,
            String expectedPackage,
            Set<String> installedSigners) {
        validateLatest(tag, release, expectedPackage, installedSigners);
        return release.versionCode() <= installedVersion ? null : release.toUpdateInfo();
    }

    static record ReleaseMetadata(
            int versionCode,
            String versionName,
            String packageName,
            String apkUrl,
            String sha256,
            String signerSha256) {
        ReleaseMetadata {
            if (versionCode < 1) throw new IllegalArgumentException("versionCode fehlt");
            if (versionName == null || versionName.isBlank()) {
                throw new IllegalArgumentException("versionName fehlt");
            }
            if (packageName == null || packageName.isBlank()) {
                throw new IllegalArgumentException("Paket-ID fehlt");
            }
            if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException("APK-Hash ist ungültig");
            }
            if (signerSha256 == null || !signerSha256.matches("[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException("Signaturfingerabdruck ist ungültig");
            }
            requireRepositoryUrl(apkUrl, APK_ASSET);
            sha256 = sha256.toLowerCase(Locale.ROOT);
            signerSha256 = signerSha256.toLowerCase(Locale.ROOT);
        }

        static ReleaseMetadata from(JSONObject source, String apkUrl)
                throws Exception {
            return new ReleaseMetadata(source.getInt("versionCode"),
                    source.getString("versionName"), source.getString("packageName"), apkUrl,
                    source.getString("sha256"), source.getString("signerSha256"));
        }

        UpdateInfo toUpdateInfo() {
            return new UpdateInfo(versionCode, versionName, packageName, apkUrl, sha256,
                    signerSha256);
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
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
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
                if (remaining <= 0) {
                    throw new IllegalStateException("Systemdownload hat Zeitlimit überschritten");
                }
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
    private record PackageEvidence(long versionCode, Set<String> signers) { }

    private PackageEvidence installedPackage() throws Exception {
        PackageInfo info = packageInfo(BuildConfig.APPLICATION_ID, null);
        if (info == null || !BuildConfig.APPLICATION_ID.equals(info.packageName)) {
            throw new SecurityException("Installiertes App-Paket ist nicht lesbar");
        }
        long version = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode() : info.versionCode;
        Set<String> signers = certificateDigests(info);
        if (signers.isEmpty()) throw new SecurityException("Installierte Signatur ist nicht lesbar");
        return new PackageEvidence(version, signers);
    }

    private void verifyPackage(File apk, UpdateInfo update) throws Exception {
        PackageInfo archive = packageInfo(null, apk);
        PackageInfo installed = packageInfo(BuildConfig.APPLICATION_ID, null);
        UpdatePackageVerifier.verify(apk, update, BuildConfig.VERSION_CODE,
                BuildConfig.APPLICATION_ID, evidence(installed), evidence(archive));
    }

    private PackageInfo packageInfo(String packageName, File archive) throws Exception {
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        return archive == null
                ? manager.getPackageInfo(packageName, flags)
                : manager.getPackageArchiveInfo(archive.getAbsolutePath(), flags);
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
        requireRepositoryUrl(value, name);
        return value;
    }

    private static void requireRepositoryUrl(String value, String asset) {
        String prefix = "https://github.com/ThonkTank/AI-Secretary/releases/download/";
        if (value == null || !value.startsWith(prefix) || !value.endsWith("/" + asset)) {
            throw new IllegalArgumentException("Release-Asset stammt nicht aus diesem Repository");
        }
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
