package repository;

import java.util.List;
import java.util.Map;

/**
 * Interface für Datenbankzugriff. Wird von SQLrepo implementiert und kann für Tests gemockt werden.
 */
public interface Repo {
    <T> T lookup(String table, Map<String, String> filters, String column);
    <T> List<T> lookups(String table, Map<String, String> filters, String column);
    <T> T fetch(Table<T> table, long id);
    <T> T fetch(Table<T> table, Map<String, String> filters);
    <T> List<T> fetchAll(Table<T> table);
    <T> List<T> fetchAll(Table<T> table, Map<String, String> filters);
    <T> void delete(Table<T> table, long id);
    <T> void delete(Table<T> table, Map<String, String> filters);
    void write(Object entity);
}
