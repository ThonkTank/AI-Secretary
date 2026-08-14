package com.autosecretary.platform.update;

import android.content.Context;
import android.os.Environment;

import com.autosecretary.application.update.DownloadProgress;
import com.autosecretary.application.update.DownloadTicket;
import com.autosecretary.application.update.UpdateCheckResult;
import com.autosecretary.application.update.UpdateException;
import com.autosecretary.application.update.UpdateFailure;
import com.autosecretary.application.update.UpdateInfo;
import com.autosecretary.application.update.UpdateRepository;
import com.autosecretary.application.update.VerifiedUpdate;

import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/** Composes feed, transfer, package evidence and verification behind the core port. */
public final class AndroidUpdateRepository implements UpdateRepository {
    private static final String PREFERENCES = "phone_update_repository";
    private static final String PENDING = "pending_metadata";
    private static final long APK_LIMIT = 80L * 1024L * 1024L;
    private final Context context;
    private final String packageName;
    private final GitHubReleaseFeed feed;
    private final AndroidPackageEvidenceReader packages;
    private final DownloadManagerUpdateDownloader downloader;

    public AndroidUpdateRepository(
            Context context,
            String owner,
            String repository,
            String packageName,
            String appVersionName) {
        this(context, owner, repository, packageName, appVersionName,
                GitHubReleaseFeed.METADATA_ASSET, GitHubReleaseFeed.APK_ASSET);
    }

    public AndroidUpdateRepository(
            Context context,
            String owner,
            String repository,
            String packageName,
            String appVersionName,
            String metadataAsset,
            String apkAsset) {
        this.context = context.getApplicationContext();
        this.packageName = packageName;
        UrlTrustPolicy trust = new UrlTrustPolicy(owner, repository);
        HttpTransport http = new UrlConnectionHttpTransport(trust, appVersionName);
        feed = new GitHubReleaseFeed(
                owner, repository, metadataAsset, apkAsset, http, trust);
        packages = new AndroidPackageEvidenceReader(context, packageName);
        downloader = new DownloadManagerUpdateDownloader(context, appVersionName);
    }

    @Override public UpdateCheckResult check() {
        try {
            var installed = packages.installed();
            UpdateInfo latest = feed.latest(
                    installed.versionCode(), packageName, installed.signers());
            return latest == null ? new UpdateCheckResult.Current()
                    : new UpdateCheckResult.Available(latest);
        } catch (Throwable error) {
            throw failure("Update-Prüfung fehlgeschlagen", error);
        }
    }

    @Override public DownloadTicket enqueue(UpdateInfo update) {
        try {
            remember(update);
            return downloader.enqueue(update, target(update.versionCode()));
        } catch (Throwable error) {
            throw failure("Update-Download konnte nicht gestartet werden", error);
        }
    }

    @Override public DownloadProgress query(DownloadTicket ticket) {
        try {
            return downloader.query(ticket);
        } catch (Throwable error) {
            return new DownloadProgress.Failed(failure(
                    "Downloadstatus konnte nicht gelesen werden", error).failure());
        }
    }

    @Override public VerifiedUpdate verify(DownloadTicket ticket) {
        File candidate = target(ticket.versionCode());
        try {
            if (!(downloader.query(ticket) instanceof DownloadProgress.Complete)) {
                throw new IllegalStateException("Systemdownload ist noch nicht vollständig");
            }
            UpdateInfo update = restore(ticket.versionCode());
            if (candidate.length() <= 0 || candidate.length() > APK_LIMIT
                    || update.apkSizeBytes() > 0
                    && candidate.length() != update.apkSizeBytes()) {
                throw new SecurityException("Update-Paket ist unerwartet groß");
            }
            var installed = packages.installed();
            UpdateArtifactVerifier.verify(candidate, update, installed.versionCode(), packageName,
                    installed, packages.archive(candidate));
            downloader.forget(ticket);
            return new VerifiedUpdate(update, candidate);
        } catch (Throwable error) {
            if (candidate.exists()) candidate.delete();
            downloader.cancel(ticket);
            clearPending();
            throw failure("Update-Paket wurde verworfen", error);
        }
    }

    @Override public void cancel(DownloadTicket ticket) {
        downloader.cancel(ticket);
        File target = target(ticket.versionCode());
        if (target.exists()) target.delete();
        clearPending();
    }

    @Override public void cleanup(long installedVersionCode) {
        File[] files = directory().listFiles((ignored, name) ->
                name.startsWith("AutoSecretary-") && name.endsWith(".apk"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(AndroidUpdateRepository::fileVersion)
                .reversed());
        boolean keptFutureUpdate = false;
        for (File file : files) {
            long version = fileVersion(file);
            if (!keptFutureUpdate && version > installedVersionCode) {
                keptFutureUpdate = true;
            } else {
                file.delete();
            }
        }
        if (!keptFutureUpdate) clearPending();
    }

    private void remember(UpdateInfo update) throws Exception {
        JSONObject value = new JSONObject()
                .put("versionCode", update.versionCode())
                .put("versionName", update.versionName())
                .put("packageName", update.packageName())
                .put("apkUrl", update.apkUrl())
                .put("apkSizeBytes", update.apkSizeBytes())
                .put("sha256", update.sha256())
                .put("signerSha256", update.signerSha256());
        if (!context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(PENDING, value.toString()).commit()) {
            throw new IllegalStateException("Update-Metadaten konnten nicht gespeichert werden");
        }
    }

    private UpdateInfo restore(int versionCode) throws Exception {
        String encoded = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(PENDING, null);
        if (encoded == null) throw new IllegalStateException("Update-Metadaten fehlen");
        JSONObject value = new JSONObject(encoded);
        UpdateInfo update = new UpdateInfo(value.getInt("versionCode"),
                value.getString("versionName"), value.getString("packageName"),
                value.getString("apkUrl"), value.getLong("apkSizeBytes"),
                value.getString("sha256"), value.getString("signerSha256"));
        if (update.versionCode() != versionCode) {
            throw new SecurityException("Download und Metadaten widersprechen sich");
        }
        return update;
    }

    private void clearPending() {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().remove(PENDING).commit();
    }

    static UpdateException failure(String context, Throwable error) {
        if (error instanceof UpdateException typed) return typed;
        Throwable source = error;
        while (source.getCause() != null && source.getCause() != source) source = source.getCause();
        String detail = source.getMessage() == null ? context : source.getMessage();
        String normalized = (source.getClass().getSimpleName() + " " + detail)
                .toLowerCase(Locale.ROOT);
        UpdateFailure.Kind kind;
        boolean retryable;
        if ((source instanceof HttpTransportException transport
                && transport.kind() == HttpTransportException.Kind.RATE_LIMITED)
                || normalized.contains("429") || normalized.contains("rate")) {
            kind = UpdateFailure.Kind.RATE_LIMITED; retryable = true;
        } else if (normalized.contains("timeout") || normalized.contains("unknownhost")
                || normalized.contains("network") || normalized.contains("offline")) {
            kind = UpdateFailure.Kind.NETWORK; retryable = true;
        } else if (source instanceof SecurityException) {
            kind = UpdateFailure.Kind.SECURITY_REJECTED; retryable = false;
        } else if (source instanceof HttpTransportException) {
            kind = UpdateFailure.Kind.INVALID_RELEASE; retryable = false;
        } else if (normalized.contains("speicher") || normalized.contains("space")) {
            kind = UpdateFailure.Kind.STORAGE; retryable = true;
        } else if (normalized.contains("download")) {
            kind = UpdateFailure.Kind.DOWNLOAD_FAILED; retryable = true;
        } else if (normalized.contains("metadata") || normalized.contains("release")) {
            kind = UpdateFailure.Kind.INVALID_RELEASE; retryable = false;
        } else {
            kind = UpdateFailure.Kind.INTERNAL; retryable = true;
        }
        return new UpdateException(new UpdateFailure(kind, detail, retryable), error);
    }

    private File target(int versionCode) {
        return new File(directory(), "AutoSecretary-" + versionCode + ".apk");
    }

    private static long fileVersion(File file) {
        String name = file.getName();
        try {
            return Long.parseLong(name.substring("AutoSecretary-".length(),
                    name.length() - ".apk".length()));
        } catch (RuntimeException invalid) {
            return -1;
        }
    }

    private File directory() {
        File external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (external == null) throw new IllegalStateException("Update-Verzeichnis fehlt");
        File directory = new File(external, "updates");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Update-Verzeichnis konnte nicht angelegt werden");
        }
        return directory;
    }
}
