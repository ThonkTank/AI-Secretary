package de.thonktank.autosecretary.domain.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/** User-defined pool whose capacity may be held across several flow steps. */
public final class CapacityResource {
    public final String id;
    public final String name;
    public final String normalizedName;
    public final int capacity;

    public CapacityResource(String id, String name, int capacity) {
        if (blank(id) || blank(name))
            throw new IllegalArgumentException("Resource identity and name are required");
        if (capacity < 1 || capacity > 1_000)
            throw new IllegalArgumentException("Resource capacity must be between 1 and 1000");
        this.id = id;
        this.name = name.trim();
        this.normalizedName = normalizeName(this.name);
        this.capacity = capacity;
    }

    public static String normalizeName(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof CapacityResource)) return false;
        CapacityResource value = (CapacityResource) other;
        return id.equals(value.id) && name.equals(value.name) && capacity == value.capacity;
    }

    @Override public int hashCode() { return Objects.hash(id, name, capacity); }
}
