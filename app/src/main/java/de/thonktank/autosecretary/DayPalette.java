package de.thonktank.autosecretary;

import java.time.LocalTime;

/** Named clock-driven design tokens shared by app and widgets. */
public final class DayPalette {
    public enum Mode { AUTO, LIGHT, DARK }

    private static final DayPaletteInterpolator INTERPOLATOR = new DayPaletteInterpolator();

    public final SurfaceTokens surfaces;
    public final TypographyTokens typography;
    public final ForestTokens forest;
    public final MotionTokens motion;

    // Compatibility aliases keep binders compact while token groups define ownership.
    public final int background, leaf1, leaf2, leaf3, ink, ink2, hint, muted, done, dot, status;
    public final int accent, accentText, light, lightText, tree, calendar, calendarInk, calendarLabel, bad;
    public final float farAlpha, middleAlpha, frontAlpha, sunX, sunWidth;
    public final int sunColor;

    DayPalette(SurfaceTokens surfaces, TypographyTokens typography,
               ForestTokens forest, MotionTokens motion) {
        this.surfaces = surfaces;
        this.typography = typography;
        this.forest = forest;
        this.motion = motion;
        background = surfaces.background;
        leaf1 = surfaces.leafPrimary;
        leaf2 = surfaces.leafSecondary;
        leaf3 = surfaces.leafTertiary;
        accent = surfaces.accent;
        accentText = surfaces.accentContent;
        light = surfaces.lightAccent;
        lightText = surfaces.lightAccentContent;
        calendar = surfaces.calendar;
        calendarInk = surfaces.calendarContent;
        calendarLabel = surfaces.calendarLabel;
        ink = typography.primary;
        ink2 = typography.secondary;
        hint = typography.hint;
        muted = typography.muted;
        done = typography.completed;
        dot = typography.control;
        status = typography.status;
        bad = typography.destructive;
        tree = forest.tree;
        farAlpha = forest.farAlpha;
        middleAlpha = forest.middleAlpha;
        frontAlpha = forest.frontAlpha;
        sunX = forest.sunX;
        sunWidth = forest.sunWidth;
        sunColor = forest.sunColor;
    }

    public static DayPalette at(LocalTime time, Mode mode) {
        return INTERPOLATOR.interpolate(time, mode);
    }

    public static int greetingRes(LocalTime time) {
        int minute = time.getHour() * 60 + time.getMinute();
        if (minute < 260) return R.string.greeting_night;
        if (minute < 390) return R.string.greeting_early;
        if (minute < 580) return R.string.greeting_morning;
        if (minute < 785) return R.string.greeting_forenoon;
        if (minute < 1030) return R.string.greeting_noon;
        if (minute < 1175) return R.string.greeting_afternoon;
        if (minute < 1300) return R.string.greeting_evening;
        if (minute < 1430) return R.string.greeting_late;
        return R.string.greeting_night;
    }

    DayPalette withSun(ForestTokens automaticForest) {
        return new DayPalette(surfaces, typography,
                new ForestTokens(forest.tree, forest.farAlpha, forest.middleAlpha,
                        forest.frontAlpha, automaticForest.sunX, automaticForest.sunWidth,
                        automaticForest.sunColor), motion);
    }

    static DayPalette day(float sunX) {
        return palette(new SurfaceTokens(0xffeef0e6, 0xfffcf6e8, 0xfff1e8d2, 0xffe2d7bc,
                        0xff2e6b44, 0xfffcfaf2, 0xffe3a542, 0xff182018,
                        0xffdfe9ec, 0xff2b5666, 0xff4f7482),
                new TypographyTokens(0xff1a2618, 0xff4e5a48, 0xff586250, 0xff6d7860,
                        0xffa79a7c, 0xffb0a385, 0xff5e6a58, 0xff9e4f3a),
                new ForestTokens(0xff2a3628, .075f, .14f, .5f,
                        sunX, 1.30f, 0xb8fff0ce));
    }

    static DayPalette evening(float sunX) {
        return palette(new SurfaceTokens(0xff12100a, 0xff2c2214, 0xff231a0f, 0xff1c150a,
                        0xfff0a03c, 0xff231a0e, 0xfff0a03c, 0xff231a0e,
                        0xff101c20, 0xff93c3d2, 0xff7099a8),
                new TypographyTokens(0xfff8ecd2, 0xffc9b694, 0xffc3ae86, 0xffa08b62,
                        0xff7a6742, 0xff7e6c48, 0xffbcab8c, 0xffc96a4e),
                new ForestTokens(0xff020201, .34f, .6f, .96f,
                        sunX, 1.04f, 0x8ff8a84a));
    }

    static DayPalette night(float sunX) {
        return palette(new SurfaceTokens(0xff080f0b, 0xff243322, 0xff1c2a1a, 0xff162113,
                        0xffe8a83e, 0xff14201a, 0xffe8a83e, 0xff14201a,
                        0xff0b171c, 0xff8fbacb, 0xff7096a6),
                new TypographyTokens(0xfff4eeda, 0xffc0c9b2, 0xffb2bca4, 0xff8e9a84,
                        0xff6b7458, 0xff68715a, 0xffa9b9ac, 0xffc96a4e),
                new ForestTokens(0xff010302, .34f, .6f, .96f,
                        sunX, .88f, 0x80f4b258));
    }

    private static DayPalette palette(SurfaceTokens surfaces, TypographyTokens typography,
                                      ForestTokens forest) {
        return new DayPalette(surfaces, typography, forest, MotionTokens.standard());
    }
}
