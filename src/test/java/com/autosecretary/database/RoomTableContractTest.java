package com.autosecretary.database;

import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Query;

import com.autosecretary.features.budget.data.BudgetDao;
import com.autosecretary.features.budget.data.BudgetLimitDao;
import com.autosecretary.features.budget.data.BudgetLookupDao;
import com.autosecretary.features.budget.data.TransactionDao;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;

public class RoomTableContractTest {

    private static final Pattern TABLE_TOKEN_PATTERN = Pattern.compile(
            "(?i)\\b(?:from|join|update|into)\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?");

    @Test
    public void budgetDaoQueriesOnlyReferenceRegisteredEntities() {
        Set<String> entityTables = getRegisteredTableNames(AppDatabase.class);
        Set<String> referencedTables = getReferencedTables(BudgetDao.class, BudgetLookupDao.class,
                TransactionDao.class, BudgetLimitDao.class);

        referencedTables.remove("coalesce");
        referencedTables.remove("case");

        Set<String> missingTables = new HashSet<>(referencedTables);
        missingTables.removeAll(entityTables);

        assertFalse("DAOs reference tables missing from AppDatabase entities: " + missingTables,
                !missingTables.isEmpty());
    }

    private Set<String> getRegisteredTableNames(Class<?> databaseClass) {
        Database annotation = databaseClass.getAnnotation(Database.class);
        Set<String> tables = new HashSet<>();
        for (Class<?> entityClass : annotation.entities()) {
            Entity entity = entityClass.getAnnotation(Entity.class);
            String explicitTable = entity.tableName();
            if (explicitTable == null || explicitTable.isBlank()) {
                tables.add(entityClass.getSimpleName().toLowerCase(Locale.ROOT));
            } else {
                tables.add(explicitTable);
            }
        }
        return tables;
    }

    private Set<String> getReferencedTables(Class<?>... daoTypes) {
        Set<String> tables = new HashSet<>();
        for (Class<?> daoType : daoTypes) {
            if (!daoType.isAnnotationPresent(Dao.class)) {
                continue;
            }
            Arrays.stream(daoType.getDeclaredMethods())
                    .map(method -> method.getAnnotation(Query.class))
                    .filter(query -> query != null)
                    .map(Query::value)
                    .forEach(sql -> extractTableTokens(sql, tables));
        }
        return tables;
    }

    private void extractTableTokens(String sql, Set<String> tables) {
        Matcher matcher = TABLE_TOKEN_PATTERN.matcher(sql);
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
    }
}
