package de.thonktank.autosecretary.data.local;

import androidx.room.RoomDatabase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;

/** Single-list IN queries on Android SQLite can bind at most 999 parameters on API 26. */
final class RoomIdQueries {
    static final int LIMIT = 999;
    private final RoomDatabase db;

    RoomIdQueries(RoomDatabase db) { this.db = db; }

    /** The DAO orders by the filtered ID first, so sorted, disjoint batches can be concatenated. */
    <R> List<R> byId(List<String> ids, Function<List<String>, List<R>> query) {
        return read(ids, query, null);
    }

    <R> List<R> read(List<String> ids, Function<List<String>, List<R>> query, Comparator<R> order) {
        if (ids.isEmpty()) return new ArrayList<>();
        if (ids.size() <= LIMIT) return query.apply(ids);
        TreeSet<String> unique = new TreeSet<>(RoomIdQueries::compareText);
        unique.addAll(ids);
        List<String> sorted = new ArrayList<>(unique);
        return db.runInTransaction(() -> {
            List<R> result = new ArrayList<>();
            for (int i = 0; i < sorted.size(); i += LIMIT)
                result.addAll(query.apply(sorted.subList(i, Math.min(i + LIMIT, sorted.size()))));
            if (order != null) result.sort(order);
            return result;
        });
    }

    // SQLite's default BINARY collation orders UTF-8 text by scalar value.
    static int compareText(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            int x = a.codePointAt(i), y = b.codePointAt(j);
            if (x != y) return Integer.compare(x, y);
            i += Character.charCount(x);
            j += Character.charCount(y);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
