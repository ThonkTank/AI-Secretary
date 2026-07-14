package com.autosecretary.app.update;

final class AvailableUpdate {
    private final int versionCode;
    private final String apkDownloadUrl;
    private final String releaseName;
    private final String releasePageUrl;

    AvailableUpdate(int versionCode, String apkDownloadUrl, String releaseName, String releasePageUrl) {
        this.versionCode = versionCode;
        this.apkDownloadUrl = apkDownloadUrl;
        this.releaseName = releaseName;
        this.releasePageUrl = releasePageUrl;
    }

    int versionCode() {
        return versionCode;
    }

    String apkDownloadUrl() {
        return apkDownloadUrl;
    }

    String releaseName() {
        return releaseName;
    }

    String releasePageUrl() {
        return releasePageUrl;
    }
}
