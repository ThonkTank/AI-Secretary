package com.autosecretary.platform.update;

import com.autosecretary.application.update.UpdateInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class GitHubReleaseFeed {
    static final String METADATA_ASSET = "release-metadata.json";
    static final String APK_ASSET = "AutoSecretary.apk";
    private final String latestApi;
    private final HttpTransport http;
    private final UrlTrustPolicy trust;

    GitHubReleaseFeed(
            String owner, String repository, HttpTransport http, UrlTrustPolicy trust) {
        latestApi = "https://api.github.com/repos/" + owner + "/" + repository
                + "/releases/latest";
        this.http = http;
        this.trust = trust;
        trust.requireFeed(latestApi);
    }

    UpdateInfo latest(
            long installedVersion, String expectedPackage, Set<String> installedSigners)
            throws Exception {
        JSONObject release = new JSONObject(http.get(latestApi, 1_000_000));
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
            throw new SecurityException("Das neueste Update ist nicht regulär veröffentlicht");
        }
        Map<String, String> assets = assetUrls(release.getJSONArray("assets"));
        String metadataUrl = requiredAsset(assets, METADATA_ASSET);
        ReleaseMetadata metadata = ReleaseMetadata.from(
                new JSONObject(http.get(metadataUrl, 16_384)),
                requiredAsset(assets, APK_ASSET), trust);
        validate(release.getString("tag_name"),
                release.optString("target_commitish", ""), metadata,
                expectedPackage, installedSigners);
        return metadata.versionCode() <= installedVersion ? null : metadata.toUpdateInfo();
    }

    static void validate(
            String tag,
            String targetCommit,
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
        if (release.schemaVersion() == 1 && !release.commitSha().equals(targetCommit)) {
            throw new SecurityException("Freigabeziel und Metadaten-Commit widersprechen sich");
        }
    }

    private String requiredAsset(Map<String, String> assets, String name) {
        String value = assets.get(name);
        trust.requireReleaseAsset(value, name);
        return value;
    }

    private static Map<String, String> assetUrls(JSONArray source) throws Exception {
        Map<String, String> result = new HashMap<>();
        for (int index = 0; index < source.length(); index++) {
            JSONObject asset = source.getJSONObject(index);
            result.put(asset.getString("name"), asset.getString("browser_download_url"));
        }
        return result;
    }

    static record ReleaseMetadata(
            int schemaVersion,
            int versionCode,
            String versionName,
            String packageName,
            String apkUrl,
            long apkSizeBytes,
            String sha256,
            String signerSha256,
            String commitSha) {
        ReleaseMetadata {
            if (schemaVersion < 0 || schemaVersion > 1) {
                throw new IllegalArgumentException("Metadaten-Schema wird nicht unterstützt");
            }
            if (versionCode < 1 || versionName == null || versionName.isBlank()) {
                throw new IllegalArgumentException("Release-Version fehlt");
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
            if (schemaVersion == 1 && (apkSizeBytes < 1
                    || apkSizeBytes > 80L * 1024L * 1024L)) {
                throw new IllegalArgumentException("APK-Größe fehlt");
            }
            if (schemaVersion == 1
                    && (commitSha == null || !commitSha.matches("[0-9a-fA-F]{40}"))) {
                throw new IllegalArgumentException("Release-Commit fehlt");
            }
            sha256 = sha256.toLowerCase(Locale.ROOT);
            signerSha256 = signerSha256.toLowerCase(Locale.ROOT);
            commitSha = commitSha == null ? "" : commitSha.toLowerCase(Locale.ROOT);
        }

        static ReleaseMetadata from(JSONObject source, String apkUrl, UrlTrustPolicy trust)
                throws Exception {
            trust.requireReleaseAsset(apkUrl, APK_ASSET);
            int schema = source.optInt("schemaVersion", 0);
            if (!APK_ASSET.equals(source.getString("apkAsset"))) {
                throw new SecurityException("Update-Metadaten nennen ein unbekanntes APK");
            }
            return new ReleaseMetadata(schema, source.getInt("versionCode"),
                    source.getString("versionName"), source.getString("packageName"), apkUrl,
                    source.optLong("apkSizeBytes", 0), source.getString("sha256"),
                    source.getString("signerSha256"), source.optString("commitSha", ""));
        }

        UpdateInfo toUpdateInfo() {
            return new UpdateInfo(versionCode, versionName, packageName, apkUrl,
                    apkSizeBytes, sha256, signerSha256);
        }
    }
}
