package de.thonktank.autosecretary;

/** Single source of truth for the persisted database and supported production upgrade. */
public final class DatabaseContract {
    public static final int VERSION = 19;
    public static final int PRODUCTION_UPGRADE_SOURCE_VERSION = 8;

    private DatabaseContract() { }
}
