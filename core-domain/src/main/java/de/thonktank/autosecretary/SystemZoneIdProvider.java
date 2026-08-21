package de.thonktank.autosecretary;

import java.time.ZoneId;

public final class SystemZoneIdProvider implements ZoneIdProvider {
    @Override public ZoneId zoneId() {
        return ZoneId.systemDefault();
    }
}
