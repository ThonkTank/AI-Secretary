package repository;

import entities.trackedItem;
import entities.todoList;

/**
 * Typisierte Tabellen-Referenz für typsichere fetch()-Aufrufe.
 *
 * Verwendung:
 *   trackedItem item = repo.fetch(Table.ITEMS, 5);
 *   todoList list = repo.fetch(Table.TODOS, Map.of("date", "2026-01-23"));
 */
public class Table<T> {

    public static final Table<trackedItem> ITEMS = new Table<>("items");
    public static final Table<todoList> TODOS = new Table<>("todos");

    private final String name;

    private Table(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }
}
