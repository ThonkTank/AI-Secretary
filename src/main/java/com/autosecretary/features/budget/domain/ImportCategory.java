package com.autosecretary.features.budget.domain;

public class ImportCategory {
    public final String id;
    public final String name;
    public final String type;

    public ImportCategory(String id, String name, String type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }
}
