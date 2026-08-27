package de.thonktank.autosecretary;

public final class SystemMomentSource implements MomentSource {
    @Override public long nowEpochMillis() { return System.currentTimeMillis(); }
}
