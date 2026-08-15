package de.thonktank.autosecretary.update;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.function.IntConsumer;

public final class GitHubUpdateRepository implements UpdateRepository {
    private static final int MAX_RELEASE_FEED_BYTES = 1_000_000;
    private static final int MAX_METADATA_BYTES = 16_384;

    private final String packageName;
    private final String releasesUrl;
    private final String metadataAsset;
    private final String apkAsset;
    private final String tagPrefix;
    private final File updateDirectory;
    private final HttpTransport http;
    private final PackageEvidenceReader packages;

    public GitHubUpdateRepository(Context context, String owner, String repository,
                                  String metadataAsset, String apkAsset, String tagPrefix) {
        this(context.getPackageName(), owner, repository, metadataAsset, apkAsset, tagPrefix,
                new File(context.getCacheDir(), "updates"), new UrlConnectionHttpTransport(),
                new AndroidPackageEvidenceReader(context));
    }

    GitHubUpdateRepository(String packageName, String owner, String repository,
                           String metadataAsset, String apkAsset, String tagPrefix,
                           File updateDirectory, HttpTransport http,
                           PackageEvidenceReader packages) {
        this.packageName = packageName;
        this.releasesUrl = "https://api.github.com/repos/" + owner + "/" + repository
                + "/releases?per_page=30";
        this.metadataAsset = metadataAsset;
        this.apkAsset = apkAsset;
        this.tagPrefix = tagPrefix;
        this.updateDirectory = updateDirectory;
        this.http = http;
        this.packages = packages;
    }

    @Override public UpdateInfo check() throws Exception {
        PackageEvidence installed = packages.installed();
        JSONArray releases = new JSONArray(new String(http.get(releasesUrl,
                MAX_RELEASE_FEED_BYTES), StandardCharsets.UTF_8));
        JSONObject candidate = newestCompatibleRelease(releases);
        if (candidate == null) return null;
        long taggedVersion = taggedVersion(candidate.getString("tag_name"));
        if (taggedVersion <= installed.versionCode) return null;
        JSONObject assets = assetsByName(candidate.getJSONArray("assets"));
        String metadataUrl = assetUrl(assets, metadataAsset);
        String apkUrl = assetUrl(assets, apkAsset);
        ReleaseMetadata metadata = ReleaseMetadata.parse(new String(
                http.get(metadataUrl, MAX_METADATA_BYTES), StandardCharsets.UTF_8));
        validate(candidate, taggedVersion, metadata, installed);
        return new UpdateInfo(metadata, apkUrl);
    }

    @Override public VerifiedUpdate download(UpdateInfo update, IntConsumer progress)
            throws Exception {
        if (update == null || update.metadata == null || update.apkUrl == null)
            throw new IllegalArgumentException("Update does not contain a verified release contract");
        if (!updateDirectory.exists() && !updateDirectory.mkdirs())
            throw new java.io.IOException("Could not create update directory");
        clearUpdateDirectory();
        File partial = new File(updateDirectory, "update-" + update.versionCode + ".partial");
        File complete = new File(updateDirectory, "update-" + update.versionCode + ".apk");
        delete(partial);
        delete(complete);
        try {
            http.download(update.apkUrl, partial, update.sizeBytes,
                    ReleaseMetadata.MAX_APK_BYTES, progress);
            if (!sha256(partial).equals(update.metadata.sha256))
                throw new SecurityException("APK checksum does not match metadata");
            PackageEvidence installed = packages.installed();
            PackageEvidence archive = packages.archive(partial);
            if (!packageName.equals(archive.packageName)
                    || archive.versionCode != update.versionCode
                    || update.versionCode <= installed.versionCode)
                throw new SecurityException("APK package or version is incompatible");
            if (!archive.signers.contains(update.metadata.signerSha256)
                    || !installed.signers.contains(update.metadata.signerSha256))
                throw new SecurityException("APK signer is incompatible");
            if (!partial.renameTo(complete))
                throw new java.io.IOException("Could not finalize update download");
            progress.accept(100);
            return new VerifiedUpdate(update, complete);
        } catch (Exception error) {
            delete(partial);
            delete(complete);
            throw error;
        }
    }

    private JSONObject newestCompatibleRelease(JSONArray releases) throws Exception {
        JSONObject result = null;
        long best = -1;
        for (int index = 0; index < releases.length(); index++) {
            JSONObject release = releases.getJSONObject(index);
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue;
            String tag = release.optString("tag_name", "");
            if (!tag.startsWith(tagPrefix)) continue;
            long version = taggedVersion(tag);
            if (version > best) {
                best = version;
                result = release;
            }
        }
        return result;
    }

    private long taggedVersion(String tag) {
        if (!tag.startsWith(tagPrefix)) throw new SecurityException("Unexpected release tag");
        try {
            long value = Long.parseLong(tag.substring(tagPrefix.length()));
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new SecurityException("Invalid release version tag", error);
        }
    }

    private void validate(JSONObject release, long taggedVersion, ReleaseMetadata metadata,
                          PackageEvidence installed) throws Exception {
        if (metadata.versionCode != taggedVersion
                || !packageName.equals(metadata.packageName)
                || !apkAsset.equals(metadata.apkAsset)
                || metadata.versionCode <= installed.versionCode)
            throw new SecurityException("Release metadata is incompatible");
        if (!installed.signers.contains(metadata.signerSha256))
            throw new SecurityException("Release signer is incompatible");
        String target = release.optString("target_commitish", "").toLowerCase(Locale.ROOT);
        if (!metadata.commitSha.equals(target))
            throw new SecurityException("Release commit does not match metadata");
    }

    private static JSONObject assetsByName(JSONArray assets) throws Exception {
        JSONObject result = new JSONObject();
        for (int index = 0; index < assets.length(); index++) {
            JSONObject asset = assets.getJSONObject(index);
            result.put(asset.getString("name"), asset.getString("browser_download_url"));
        }
        return result;
    }

    private static String assetUrl(JSONObject assets, String name) throws Exception {
        String value = assets.optString(name, "");
        if (value.isEmpty() || !value.startsWith("https://"))
            throw new SecurityException("Release is missing " + name);
        return value;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte item : digest.digest())
            result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        return result.toString();
    }

    private static void delete(File file) {
        if (file.exists()) file.delete();
    }

    private void clearUpdateDirectory() {
        File[] files = updateDirectory.listFiles();
        if (files == null) return;
        for (File file : files) if (file.isFile()) delete(file);
    }
}
