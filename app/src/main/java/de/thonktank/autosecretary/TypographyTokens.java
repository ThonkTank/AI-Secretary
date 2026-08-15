package de.thonktank.autosecretary;

public final class TypographyTokens {
    public final int primary;
    public final int secondary;
    public final int hint;
    public final int muted;
    public final int completed;
    public final int control;
    public final int status;
    public final int destructive;

    public TypographyTokens(int primary, int secondary, int hint, int muted, int completed,
                            int control, int status, int destructive) {
        this.primary = primary;
        this.secondary = secondary;
        this.hint = hint;
        this.muted = muted;
        this.completed = completed;
        this.control = control;
        this.status = status;
        this.destructive = destructive;
    }
}
