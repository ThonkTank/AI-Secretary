package de.thonktank.autosecretary;

import java.time.LocalTime;

public final class DayPaletteInterpolator {
    public DayPalette interpolate(LocalTime time, DayPalette.Mode mode) {
        int minute = time.getHour() * 60 + time.getMinute();
        int first = DayPaletteAnchor.PRE_DAWN.minuteOfDay;
        if (minute < first) minute += 24 * 60;
        DayPaletteAnchor[] anchors = DayPaletteAnchor.values();
        DayPalette automatic;
        if (minute >= anchors[anchors.length - 1].minuteOfDay) {
            int end = first + 24 * 60;
            float fraction = fraction(minute, anchors[anchors.length - 1].minuteOfDay, end);
            automatic = mix(anchor(anchors[anchors.length - 1]),
                    anchor(DayPaletteAnchor.PRE_DAWN), fraction);
        } else {
            int index = 0;
            while (index < anchors.length - 2
                    && minute >= anchors[index + 1].minuteOfDay) index++;
            float fraction = fraction(minute, anchors[index].minuteOfDay,
                    anchors[index + 1].minuteOfDay);
            automatic = mix(anchor(anchors[index]), anchor(anchors[index + 1]), fraction);
        }
        if (mode == DayPalette.Mode.AUTO) return automatic;
        DayPalette fixed = mode == DayPalette.Mode.LIGHT
                ? DayPalette.day(automatic.forest.sunX) : DayPalette.night(automatic.forest.sunX);
        // Fixed modes freeze surfaces/tree density; sun position, width and color still follow time.
        return fixed.withSun(automatic.forest);
    }

    public DayPalette atAnchor(DayPaletteAnchor anchor) {
        return anchor(anchor);
    }

    private static DayPalette anchor(DayPaletteAnchor anchor) {
        switch (anchor) {
            case PRE_DAWN: return DayPalette.night(96);
            case DAWN: return mix(DayPalette.night(96), DayPalette.day(82), .55f);
            case MORNING: return DayPalette.day(66);
            case FORENOON: return DayPalette.day(50);
            case NOON: return mix(DayPalette.day(28), DayPalette.evening(28), .35f);
            case AFTERNOON: return DayPalette.evening(6);
            case EVENING: return mix(DayPalette.evening(2), DayPalette.night(2), .65f);
            case NIGHT: return DayPalette.night(96);
            default: throw new IllegalArgumentException("Unknown anchor " + anchor);
        }
    }

    static DayPalette mix(DayPalette a, DayPalette b, float amount) {
        int background = color(a.background, b.background, amount);
        int leafPrimary = color(a.leaf1, b.leaf1, amount);
        int accent = color(a.accent, b.accent, amount);
        int calendar = color(a.calendar, b.calendar, amount);
        SurfaceTokens surfaces = new SurfaceTokens(
                background, leafPrimary,
                color(a.leaf2, b.leaf2, amount), color(a.leaf3, b.leaf3, amount),
                accent, accessible(color(a.accentText, b.accentText, amount), accent,
                        a.accentText, b.accentText),
                color(a.light, b.light, amount), color(a.lightText, b.lightText, amount),
                calendar, accessible(color(a.calendarInk, b.calendarInk, amount), calendar,
                        a.calendarInk, b.calendarInk),
                color(a.calendarLabel, b.calendarLabel, amount));
        TypographyTokens typography = new TypographyTokens(
                accessible(color(a.ink, b.ink, amount), leafPrimary, a.ink, b.ink),
                color(a.ink2, b.ink2, amount),
                color(a.hint, b.hint, amount), color(a.muted, b.muted, amount),
                color(a.done, b.done, amount), color(a.dot, b.dot, amount),
                accessible(color(a.status, b.status, amount), background, a.status, b.status),
                color(a.bad, b.bad, amount));
        ForestTokens forest = new ForestTokens(color(a.tree, b.tree, amount),
                value(a.farAlpha, b.farAlpha, amount),
                value(a.middleAlpha, b.middleAlpha, amount),
                value(a.frontAlpha, b.frontAlpha, amount), value(a.sunX, b.sunX, amount),
                value(a.sunWidth, b.sunWidth, amount), color(a.sunColor, b.sunColor, amount));
        return new DayPalette(surfaces, typography, forest, MotionTokens.standard());
    }

    private static float fraction(int value, int start, int end) {
        return (value - start) / (float) (end - start);
    }

    private static float value(float a, float b, float amount) {
        return a + (b - a) * amount;
    }

    private static int color(int a, int b, float amount) {
        int aa = (a >>> 24) & 255, ar = (a >>> 16) & 255, ag = (a >>> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >>> 16) & 255, bg = (b >>> 8) & 255, bb = b & 255;
        return ((int) value(aa, ba, amount) << 24) | ((int) value(ar, br, amount) << 16)
                | ((int) value(ag, bg, amount) << 8) | (int) value(ab, bb, amount);
    }

    private static int accessible(int interpolated, int background, int first, int second) {
        if (contrast(interpolated, background) >= 4.5d) return interpolated;
        int best = contrast(first, background) >= contrast(second, background) ? first : second;
        if (contrast(best, background) >= 4.5d) return best;
        return contrast(0xff000000, background) >= contrast(0xffffffff, background)
                ? 0xff000000 : 0xffffffff;
    }

    private static double contrast(int first, int second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + .05d) / (Math.min(a, b) + .05d);
    }

    private static double luminance(int color) {
        return .2126d * channel((color >> 16) & 255)
                + .7152d * channel((color >> 8) & 255) + .0722d * channel(color & 255);
    }

    private static double channel(int value) {
        double normalized = value / 255d;
        return normalized <= .04045d ? normalized / 12.92d
                : Math.pow((normalized + .055d) / 1.055d, 2.4d);
    }
}
