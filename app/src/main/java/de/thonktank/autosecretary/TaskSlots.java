package de.thonktank.autosecretary;

public final class TaskSlots {
    public static final String MORNING = "Morgen";
    public static final String MIDDAY = "Mittag";
    public static final String EVENING = "Abend";
    public static final String LATER = "Später";
    private TaskSlots() { }
    public static int rank(String slot) { if (MORNING.equals(slot)) return 0; if (MIDDAY.equals(slot)) return 1; if (EVENING.equals(slot)) return 2; return 3; }
}
