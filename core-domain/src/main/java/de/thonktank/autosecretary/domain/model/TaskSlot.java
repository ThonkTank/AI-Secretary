package de.thonktank.autosecretary.domain.model;

public enum TaskSlot {
    MORNING(0, 8 * 60),
    MIDDAY(1, 12 * 60),
    EVENING(2, 18 * 60),
    LATER(3, 21 * 60);

    public final String storageCode;
    public final int rank;
    public final int anchorMinute;

    TaskSlot(int rank, int anchorMinute) {
        this.storageCode = name();
        this.rank = rank;
        this.anchorMinute = anchorMinute;
    }

    public static TaskSlot fromStorage(String value) {
        if (value == null) throw new IllegalArgumentException("Task slot must not be null");
        try {
            return valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unsupported task slot: " + value, error);
        }
    }
}
