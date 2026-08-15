package de.thonktank.autosecretary.update.application;

/** Explicit environment-specific policy and immutable GitHub release coordinates. */
public final class UpdateConfiguration {
    public enum Environment { PRODUCTION, DEVELOPMENT, TEST }

    public final Environment environment;
    public final boolean remoteChecksEnabled;
    public final boolean automaticChecksEnabled;
    public final String repositoryOwner;
    public final String repositoryName;
    public final String metadataAsset;
    public final String apkAsset;
    public final String tagPrefix;

    private UpdateConfiguration(Environment environment, boolean remoteChecksEnabled,
                                boolean automaticChecksEnabled, String repositoryOwner,
                                String repositoryName, String metadataAsset, String apkAsset,
                                String tagPrefix) {
        this.environment = environment;
        this.remoteChecksEnabled = remoteChecksEnabled;
        this.automaticChecksEnabled = automaticChecksEnabled;
        this.repositoryOwner = requireValue(repositoryOwner, "repository owner");
        this.repositoryName = requireValue(repositoryName, "repository name");
        this.metadataAsset = requireValue(metadataAsset, "metadata asset");
        this.apkAsset = requireValue(apkAsset, "APK asset");
        this.tagPrefix = requireValue(tagPrefix, "tag prefix");
        if (automaticChecksEnabled && !remoteChecksEnabled)
            throw new IllegalArgumentException("Automatic checks require remote checks");
    }

    public static UpdateConfiguration production(String owner, String repository,
                                                 String metadataAsset, String apkAsset,
                                                 String tagPrefix) {
        return new UpdateConfiguration(Environment.PRODUCTION, true, true, owner, repository,
                metadataAsset, apkAsset, tagPrefix);
    }

    public static UpdateConfiguration development(String owner, String repository,
                                                  String metadataAsset, String apkAsset,
                                                  String tagPrefix) {
        return new UpdateConfiguration(Environment.DEVELOPMENT, false, false, owner, repository,
                metadataAsset, apkAsset, tagPrefix);
    }

    public static UpdateConfiguration test(String owner, String repository,
                                           String metadataAsset, String apkAsset,
                                           String tagPrefix) {
        return new UpdateConfiguration(Environment.TEST, false, false, owner, repository,
                metadataAsset, apkAsset, tagPrefix);
    }

    private static String requireValue(String value, String label) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("Missing update " + label);
        return value;
    }
}
