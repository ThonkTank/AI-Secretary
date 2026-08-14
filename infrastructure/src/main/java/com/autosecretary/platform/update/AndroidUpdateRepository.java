package com.autosecretary.platform.update;

import android.content.Context;
import android.os.Environment;

import com.autosecretary.application.update.UpdateInfo;
import com.autosecretary.application.update.UpdateRepository;
import com.autosecretary.application.update.VerifiedUpdate;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/** Composes the feed, transfer, package evidence and pure verification policies. */
public final class AndroidUpdateRepository implements UpdateRepository {
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
        this.context = context.getApplicationContext();
        this.packageName = packageName;
        UrlTrustPolicy trust = new UrlTrustPolicy(owner, repository);
        HttpTransport http = new UrlConnectionHttpTransport(trust, appVersionName);
        feed = new GitHubReleaseFeed(owner, repository, http, trust);
        packages = new AndroidPackageEvidenceReader(context, packageName);
        downloader = new DownloadManagerUpdateDownloader(context, appVersionName);
    }

    @Override public UpdateInfo check() {
        try {
            var installed = packages.installed();
            return feed.latest(installed.versionCode(), packageName, installed.signers());
        } catch (Exception error) {
            throw new IllegalStateException("Update-Prüfung fehlgeschlagen", error);
        }
    }

    @Override public VerifiedUpdate downloadAndVerify(UpdateInfo update) {
        File target = target(update.versionCode());
        try {
            downloader.download(update, target);
            if (target.length() <= 0 || target.length() > APK_LIMIT
                    || update.apkSizeBytes() > 0 && target.length() != update.apkSizeBytes()) {
                throw new SecurityException("Update-Paket ist unerwartet groß");
            }
            var installed = packages.installed();
            UpdatePackageVerifier.verify(target, update, installed.versionCode(), packageName,
                    installed, packages.archive(target));
            return new VerifiedUpdate(update, target);
        } catch (Exception error) {
            target.delete();
            throw new IllegalStateException("Update-Paket wurde verworfen", error);
        }
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
