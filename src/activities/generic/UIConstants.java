package activities.generic;

/**
 * Gemeinsame UI-Konstanten für alle Activity/View-Builder Klassen.
 * Farben, Textgrößen, Abstände.
 *
 * Werte gespiegelt in res/values/colors.xml und res/values/dimens.xml.
 * Statische Layouts nutzen die XML-Ressourcen, dynamischer Code nutzt diese Konstanten.
 */
public final class UIConstants {

    private UIConstants() {}

    // === Farben ===
    public static final int ACCENT          = 0xFF1976D2;
    public static final int TEXT_PRIMARY     = 0xFF212121;
    public static final int TEXT_SECONDARY   = 0xFF757575;
    public static final int TEXT_MUTED       = 0xFF9E9E9E;
    public static final int TEXT_ERROR       = 0xFFD32F2F;

    public static final int SURFACE          = 0xFFFAFAFA;
    public static final int SURFACE_COMPLETE = 0xFFE8F5E9;
    public static final int BUTTON_INACTIVE  = 0xFFE0E0E0;
    public static final int OVERLAY_DIM      = 0x80000000;

    // Typ-Badge Farben
    public static final int BADGE_PROJECT    = 0xFF7B1FA2;
    public static final int BADGE_BLOCK      = 0xFFE65100;
    public static final int BADGE_GOAL       = 0xFF1976D2;
    public static final int BADGE_TASK       = 0xFF388E3C;

    // Goal-Header
    public static final int HEADER_BG        = 0xFF455A64;

    // Kalender-Event
    public static final int CALENDAR_EVENT_BG = 0xFFE3F2FD;

    // === Abstände (DP) ===
    public static final int CORNER_RADIUS    = 4;

    // === Textgrößen (SP) ===
    public static final int SP_BADGE         = 11;
    public static final int SP_SMALL         = 12;
    public static final int SP_BODY          = 14;
    public static final int SP_TITLE         = 15;
    public static final int SP_HEADING       = 18;
}
