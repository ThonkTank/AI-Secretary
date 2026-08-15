package de.thonktank.autosecretary.update.infrastructure;

import android.content.Context;

import de.thonktank.autosecretary.update.application.UpdateRepository;
import de.thonktank.autosecretary.update.application.VerifiedUpdate;
import de.thonktank.autosecretary.update.domain.PackageEvidence;
import de.thonktank.autosecretary.update.domain.ReleaseMetadata;
import de.thonktank.autosecretary.update.domain.UpdateCheckResult;
import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateInfo;
import de.thonktank.autosecretary.update.domain.UpdateRules;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private final ReleaseMetadataJsonParser metadataParser = new ReleaseMetadataJsonParser();

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

    @Override public UpdateCheckResult check() throws UpdateFailure {
        try {
            PackageEvidence installed = packages.installed();
            JSONArray releases = new JSONArray(new String(http.get(releasesUrl,
                    MAX_RELEASE_FEED_BYTES), StandardCharsets.UTF_8));
            JSONObject candidate = newestCompatibleRelease(releases);
            if (candidate == null) return UpdateCheckResult.current();
            long taggedVersion = taggedVersion(candidate.getString("tag_name"));
            if (taggedVersion <= installed.versionCode) return UpdateCheckResult.current();
            JSONObject assets = assetsByName(candidate.getJSONArray("assets"));
            String metadataUrl = assetUrl(assets, metadataAsset);
            String apkUrl = assetUrl(assets, apkAsset);
            ReleaseMetadata metadata = metadataParser.parse(new String(
                    http.get(metadataUrl, MAX_METADATA_BYTES), StandardCharsets.UTF_8));
            UpdateRules.requireAvailable(packageName, apkAsset, taggedVersion,
                    candidate.optString("target_commitish", ""), metadata, installed);
            return UpdateCheckResult.available(UpdateInfo.from(metadata, apkUrl));
        } catch (UpdateFailure error) {
            throw error;
        } catch (JSONException error) {
            throw new UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE,
                    "GitHub release feed is malformed", error);
        }
    }

    @Override public VerifiedUpdate download(UpdateInfo update, IntConsumer progress)
            throws UpdateFailure {
        if (update == null)
            throw new UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE,
                    "Update release contract is missing");
        if (!updateDirectory.exists() && !updateDirectory.mkdirs())
            throw storage("Could not create update directory", null);
        clearUpdateDirectory();
        File partial = new File(updateDirectory, "update-" + update.versionCode + ".partial");
        File complete = new File(updateDirectory, "update-" + update.versionCode + ".apk");
        delete(partial);
        delete(complete);
        try {
            http.download(update.apkUri().toString(), partial, update.sizeBytes,
                    ReleaseMetadata.MAX_APK_BYTES, progress);
            if (!sha256(partial).equals(update.metadata().sha256))
                throw new UpdateFailure(UpdateFailure.Kind.CHECKSUM_MISMATCH,
                        "APK checksum does not match metadata");
            PackageEvidence installed = packages.installed();
            PackageEvidence archive = packages.archive(partial);
            UpdateRules.requireDownloaded(packageName, update, installed, archive);
            if (!partial.renameTo(complete))
                throw storage("Could not finalize update download", null);
            progress.accept(100);
            return VerifiedUpdate.fromVerifiedFile(update, complete);
        } catch (UpdateFailure error) {
            delete(partial);
            delete(complete);
            throw error;
        }
    }

    private JSONObject newestCompatibleRelease(JSONArray releases)
            throws JSONException, UpdateFailure {
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

    private long taggedVersion(String tag) throws UpdateFailure {
        if (!tag.startsWith(tagPrefix))
            throw new UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE,
                    "Unexpected release tag");
        try {
            long value = Long.parseLong(tag.substring(tagPrefix.length()));
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE,
                    "Invalid release version tag", error);
        }
    }

    private static JSONObject assetsByName(JSONArray assets) throws JSONException {
        JSONObject result = new JSONObject();
        for (int index = 0; index < assets.length(); index++) {
            JSONObject asset = assets.getJSONObject(index);
            result.put(asset.getString("name"), asset.getString("browser_download_url"));
        }
        return result;
    }

    private static String assetUrl(JSONObject assets, String name)
            throws JSONException, UpdateFailure {
        String value = assets.optString(name, "");
        if (value.isEmpty() || !value.startsWith("https://"))
            throw new UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE,
                    "Release is missing " + name);
        return value;
    }

    private static String sha256(File file) throws UpdateFailure {
        try {
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
        } catch (IOException error) {
            throw storage("Could not read downloaded update", error);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("Android runtime does not provide SHA-256", error);
        }
    }

    private static void delete(File file) {
        if (file.exists()) file.delete();
    }

    private void clearUpdateDirectory() {
        File[] files = updateDirectory.listFiles();
        if (files == null) return;
        for (File file : files) if (file.isFile()) delete(file);
    }

    private static UpdateFailure storage(String message, Throwable cause) {
        return new UpdateFailure(UpdateFailure.Kind.STORAGE, message, cause);
    }
}
