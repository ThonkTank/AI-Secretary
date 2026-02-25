package com.autosecretary.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalTime;
import java.time.LocalDate;
import java.time.DayOfWeek;

public class Preferences {
    private Context context; 
    private SharedPreferences prefs;

    public Preferences(Context c) {
        this.context = c;
        this.prefs = context.getSharedPreferences("user_prefs", context.MODE_PRIVATE);
    }

    //planning prefs
    public void writePrefTime(DayOfWeek day, boolean start, LocalTime value) {
        String key = prefKey(day, start);
        prefs.edit().putString(key, value.toString()).apply();
    }
    public LocalTime readPrefTime(LocalDate day, boolean start) {
        DayOfWeek dayOfWeek = day.getDayOfWeek();
        String key = prefKey(dayOfWeek, start);
        String defaultValue = start ? "06:00" : "21:00";
        return LocalTime.parse(prefs.getString(key, defaultValue));
    }
    private String prefKey(DayOfWeek day, boolean start) {return day.toString()+"_"+ (start ? "start" : "end");}
}
