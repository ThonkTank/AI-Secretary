package constants;

public enum Priority {
    LOW(100),
    MEDIUM(200),
    HIGH(400),
    CRITICAL(10000);

    public final int value;
    Priority(int value) { this.value = value; }
}
