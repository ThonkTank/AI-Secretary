package de.thonktank.autosecretary;

import java.time.LocalTime;

/** Clock-driven design tokens. Color channels are interpolated once per minute. */
final class DayPalette {
    enum Mode { AUTO, LIGHT, DARK }

    final int background, leaf1, leaf2, leaf3, ink, ink2, hint, muted, done, dot, status;
    final int accent, accentText, light, lightText, tree, calendar, calendarInk, calendarLabel, bad;
    final float farAlpha, middleAlpha, frontAlpha, shadowAlpha, sunX, sunWidth;
    final int sunColor;

    private DayPalette(int background, int leaf1, int leaf2, int leaf3, int ink, int ink2, int hint,
                       int muted, int done, int dot, int status, int accent, int accentText, int light,
                       int lightText, int tree, int calendar, int calendarInk, int calendarLabel, int bad,
                       float farAlpha, float middleAlpha, float frontAlpha, float shadowAlpha,
                       float sunX, float sunWidth, int sunColor) {
        this.background = background; this.leaf1 = leaf1; this.leaf2 = leaf2; this.leaf3 = leaf3;
        this.ink = ink; this.ink2 = ink2; this.hint = hint; this.muted = muted; this.done = done;
        this.dot = dot; this.status = status; this.accent = accent; this.accentText = accentText;
        this.light = light; this.lightText = lightText; this.tree = tree; this.calendar = calendar;
        this.calendarInk = calendarInk; this.calendarLabel = calendarLabel; this.bad = bad;
        this.farAlpha = farAlpha; this.middleAlpha = middleAlpha; this.frontAlpha = frontAlpha;
        this.shadowAlpha = shadowAlpha; this.sunX = sunX; this.sunWidth = sunWidth; this.sunColor = sunColor;
    }

    static DayPalette at(LocalTime time, Mode mode) {
        int minute = time.getHour() * 60 + time.getMinute(); if (minute < 260) minute += 1440;
        int[] marks = {260, 390, 580, 785, 1030, 1175, 1300, 1430, 1700};
        DayPalette[] values = {night(96), mix(night(96), day(82), .55f), day(66), day(50),
                mix(day(28), evening(28), .35f), evening(6), mix(evening(2), night(2), .65f), night(96), night(96)};
        int segment = 0; while (segment < marks.length - 2 && minute >= marks[segment + 1]) segment++;
        float fraction = (minute - marks[segment]) / (float) (marks[segment + 1] - marks[segment]);
        DayPalette automatic = mix(values[segment], values[segment + 1], fraction);
        DayPalette fixed = mode == Mode.LIGHT ? day(automatic.sunX) : mode == Mode.DARK ? night(automatic.sunX) : automatic;
        return fixed.withSun(automatic.sunX, automatic.sunWidth, automatic.sunColor);
    }

    static String greeting(LocalTime time) {
        int minute = time.getHour() * 60 + time.getMinute();
        if (minute < 260) return "Gute Nacht";
        if (minute < 390) return "Noch früh";
        if (minute < 580) return "Guten Morgen";
        if (minute < 785) return "Vormittag";
        if (minute < 1030) return "Mittag";
        if (minute < 1175) return "Nachmittag";
        if (minute < 1300) return "Guten Abend";
        if (minute < 1430) return "Es wird spät";
        return "Gute Nacht";
    }

    private DayPalette withSun(float x, float width, int color) {
        return new DayPalette(background, leaf1, leaf2, leaf3, ink, ink2, hint, muted, done, dot,
                status, accent, accentText, light, lightText, tree, calendar, calendarInk, calendarLabel,
                bad, farAlpha, middleAlpha, frontAlpha, shadowAlpha, x, width, color);
    }

    private static DayPalette day(float sunX) {
        return new DayPalette(0xffeef0e6, 0xfffcf6e8, 0xfff1e8d2, 0xffe2d7bc, 0xff1a2618,
                0xff4e5a48, 0xff586250, 0xff6d7860, 0xffa79a7c, 0xffb0a385, 0xff5e6a58,
                0xff2e6b44, 0xfffcfaf2, 0xffe3a542, 0xff182018, 0xff2a3628, 0xffdfe9ec,
                0xff2b5666, 0xff4f7482, 0xff9e4f3a, .075f, .14f, .5f, .16f,
                sunX, 1.30f, 0xb8fff0ce);
    }

    private static DayPalette evening(float sunX) {
        return new DayPalette(0xff12100a, 0xff2c2214, 0xff231a0f, 0xff1c150a, 0xfff8ecd2,
                0xffc9b694, 0xffc3ae86, 0xffa08b62, 0xff7a6742, 0xff7e6c48, 0xffbcab8c,
                0xfff0a03c, 0xff231a0e, 0xfff0a03c, 0xff231a0e, 0xff020201, 0xff101c20,
                0xff93c3d2, 0xff7099a8, 0xffc96a4e, .34f, .6f, .96f, .44f,
                sunX, 1.04f, 0x8ff8a84a);
    }

    private static DayPalette night(float sunX) {
        return new DayPalette(0xff080f0b, 0xff243322, 0xff1c2a1a, 0xff162113, 0xfff4eeda,
                0xffc0c9b2, 0xffb2bca4, 0xff8e9a84, 0xff6b7458, 0xff68715a, 0xffa9b9ac,
                0xffe8a83e, 0xff14201a, 0xffe8a83e, 0xff14201a, 0xff010302, 0xff0b171c,
                0xff8fbacb, 0xff7096a6, 0xffc96a4e, .34f, .6f, .96f, .44f,
                sunX, .88f, 0x80f4b258);
    }

    private static DayPalette mix(DayPalette a, DayPalette b, float t) {
        return new DayPalette(c(a.background,b.background,t), c(a.leaf1,b.leaf1,t), c(a.leaf2,b.leaf2,t),
                c(a.leaf3,b.leaf3,t), c(a.ink,b.ink,t), c(a.ink2,b.ink2,t), c(a.hint,b.hint,t),
                c(a.muted,b.muted,t), c(a.done,b.done,t), c(a.dot,b.dot,t), c(a.status,b.status,t),
                c(a.accent,b.accent,t), c(a.accentText,b.accentText,t), c(a.light,b.light,t),
                c(a.lightText,b.lightText,t), c(a.tree,b.tree,t), c(a.calendar,b.calendar,t),
                c(a.calendarInk,b.calendarInk,t), c(a.calendarLabel,b.calendarLabel,t), c(a.bad,b.bad,t),
                f(a.farAlpha,b.farAlpha,t), f(a.middleAlpha,b.middleAlpha,t), f(a.frontAlpha,b.frontAlpha,t),
                f(a.shadowAlpha,b.shadowAlpha,t), f(a.sunX,b.sunX,t), f(a.sunWidth,b.sunWidth,t), c(a.sunColor,b.sunColor,t));
    }

    private static float f(float a, float b, float t) { return a + (b - a) * t; }
    private static int c(int a, int b, float t) {
        int aa = (a >>> 24) & 255, ar = (a >>> 16) & 255, ag = (a >>> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >>> 16) & 255, bg = (b >>> 8) & 255, bb = b & 255;
        return ((int) f(aa,ba,t) << 24) | ((int) f(ar,br,t) << 16) | ((int) f(ag,bg,t) << 8) | (int) f(ab,bb,t);
    }
}
