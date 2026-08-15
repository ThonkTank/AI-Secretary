package de.thonktank.autosecretary.update;

public final class UpdateInfo {
    public final long versionCode;
    public final String versionName;
    public final long sizeBytes;
    final ReleaseMetadata metadata;
    final String apkUrl;

    public UpdateInfo(long versionCode, String versionName, long sizeBytes) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.sizeBytes = sizeBytes;
        this.metadata = null;
        this.apkUrl = null;
    }

    UpdateInfo(ReleaseMetadata metadata, String apkUrl) {
        this.versionCode = metadata.versionCode;
        this.versionName = metadata.versionName;
        this.sizeBytes = metadata.apkSizeBytes;
        this.metadata = metadata;
        this.apkUrl = apkUrl;
    }
}
