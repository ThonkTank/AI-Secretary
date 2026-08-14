package com.autosecretary.platform.update;

import com.autosecretary.application.update.UpdateInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class GitHubReleaseFeed {
    static final String METADATA_ASSET = "release-metadata.json";
    static final String APK_ASSET = "AutoSecretary.apk";
    private final String latestApi;
    private final HttpTransport http;
    private final UrlTrustPolicy trust;
    private final ReleaseMetadataParser metadataParser;
    private final String metadataAsset;
    private final String apkAsset;

    GitHubReleaseFeed(
            String owner, String repository, HttpTransport http, UrlTrustPolicy trust) {
        this(owner, repository, METADATA_ASSET, APK_ASSET, http, trust);
    }

    GitHubReleaseFeed(
            String owner,
            String repository,
            String metadataAsset,
            String apkAsset,
            HttpTransport http,
            UrlTrustPolicy trust) {
        latestApi = "https://api.github.com/repos/" + owner + "/" + repository
                + "/releases/latest";
        this.http = http;
        this.trust = trust;
        this.metadataParser = new ReleaseMetadataParser();
        this.metadataAsset = metadataAsset;
        this.apkAsset = apkAsset;
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
        String metadataUrl = requiredAsset(assets, metadataAsset);
        ReleaseMetadata metadata = metadataParser.parse(http.get(metadataUrl, 16_384),
                requiredAsset(assets, apkAsset), trust, apkAsset);
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

}
