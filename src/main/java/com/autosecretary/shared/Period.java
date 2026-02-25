package com.autosecretary.shared;

public enum Period {
    DAY(1),
    WEEK(7),
    MONTH(30);

    public final int value;
    Period(int value) { this.value = value; }
}
