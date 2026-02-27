package com.autosecretary.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalTime;
import java.time.DayOfWeek;

public class Preferences {
    private static final String DEFAULT_DAY_START = "06:00";
    private static final String DEFAULT_DAY_END = "21:00";

    private final SharedPreferences prefs;

    public Preferences(Context c) {
        this.prefs = c.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
    }

    public LocalTime readDayStartTime(DayOfWeek day) {
        String key = prefKey(day, true);
        return LocalTime.parse(prefs.getString(key, DEFAULT_DAY_START));
    }

    public LocalTime readDayEndTime(DayOfWeek day) {
        String key = prefKey(day, false);
        return LocalTime.parse(prefs.getString(key, DEFAULT_DAY_END));
    }

    private String prefKey(DayOfWeek day, boolean start) {
        return day.toString() + "_" + (start ? "start" : "end");
    }
}
