package com.autosecretary.features.meal.domain;

/** Status for shopping-list items. */
public enum ShoppingItemStatus {
    OPEN(0),
    DONE(1);

    public final int dbValue;

    ShoppingItemStatus(int dbValue) {
        this.dbValue = dbValue;
    }

    public static ShoppingItemStatus fromDbValue(int dbValue) {
        return dbValue == DONE.dbValue ? DONE : OPEN;
    }
}

