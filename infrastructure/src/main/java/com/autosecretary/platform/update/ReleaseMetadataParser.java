package com.autosecretary.platform.update;

import org.json.JSONObject;

/** Explicit compatibility parser for legacy metadata and schema version 1. */
final class ReleaseMetadataParser {
    ReleaseMetadata parse(String json, String apkUrl, UrlTrustPolicy trust) throws Exception {
        return parse(json, apkUrl, trust, GitHubReleaseFeed.APK_ASSET);
    }

    ReleaseMetadata parse(
            String json, String apkUrl, UrlTrustPolicy trust, String apkAsset) throws Exception {
        JSONObject source = new JSONObject(json);
        trust.requireReleaseAsset(apkUrl, apkAsset);
        int schema = source.has("schemaVersion") ? source.getInt("schemaVersion") : 0;
        if (schema != 0 && schema != 1) {
            throw new IllegalArgumentException("Metadaten-Schema wird nicht unterstützt: " + schema);
        }
        if (!apkAsset.equals(source.getString("apkAsset"))) {
            throw new SecurityException("Update-Metadaten nennen ein unbekanntes APK");
        }
        return new ReleaseMetadata(schema, source.getInt("versionCode"),
                source.getString("versionName"), source.getString("packageName"), apkUrl,
                source.optLong("apkSizeBytes", 0), source.getString("sha256"),
                source.getString("signerSha256"), source.optString("commitSha", ""));
    }
}
