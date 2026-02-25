package com.autosecretary.features.budget;

import android.content.Context;

import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class BudgetDaoRoomTest {

    private TestBudgetDb db;
    private BudgetDao dao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, TestBudgetDb.class)
            .allowMainThreadQueries()
            .build();
        dao = db.dao();

        dao.insertAccount(new AccountEntity(1L, "Giro"));
        dao.insertAccount(new AccountEntity(2L, "Bargeld"));

        dao.insertCategory(new CategoryEntity(12L, "Lebensmittel"));
        dao.insertCategory(new CategoryEntity(13L, "Restaurant"));

        // Legacy-nahe Referenzwerte aus SeedTestData (Februar 2026)
        dao.insertTransaction(new TransactionEntity(1L, 1L, 12L, -4_532, "2026-02-01", "2026-02"));
        dao.insertTransaction(new TransactionEntity(2L, 1L, 13L, -2_850, "2026-02-02", "2026-02"));
        dao.insertTransaction(new TransactionEntity(3L, 1L, 12L, -9_900, "2026-02-01", "2026-02"));
        dao.insertTransaction(new TransactionEntity(4L, 2L, 12L, -2_500, "2026-02-07", "2026-02"));
        dao.insertTransaction(new TransactionEntity(5L, 1L, 12L, -1_299, "2026-01-15", "2026-01"));
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void aggregateQueries_sumExpensesByMonth() {
        int febExpense = dao.sumExpensesForMonth("2026-02");
        int janExpense = dao.sumExpensesForMonth("2026-01");

        assertEquals(19_782, febExpense);
        assertEquals(1_299, janExpense);
    }

    @Test
    public void filtersByMonthCategoryAndAccount_returnExpectedRows() {
        List<TransactionEntity> febAll = dao.filter("2026-02", null, null);
        assertEquals(4, febAll.size());

        List<TransactionEntity> febGroceries = dao.filter("2026-02", 12L, null);
        assertEquals(3, febGroceries.size());

        List<TransactionEntity> febGroceriesCash = dao.filter("2026-02", 12L, 2L);
        assertEquals(1, febGroceriesCash.size());
        assertEquals(-2_500, febGroceriesCash.get(0).amountCents);
    }

    @Database(entities = {TransactionEntity.class, AccountEntity.class, CategoryEntity.class}, version = 1, exportSchema = false)
    abstract static class TestBudgetDb extends RoomDatabase {
        abstract BudgetDao dao();
    }

    @Dao
    interface BudgetDao {
        @Insert
        void insertTransaction(TransactionEntity tx);

        @Insert
        void insertAccount(AccountEntity account);

        @Insert
        void insertCategory(CategoryEntity category);

        @Query("SELECT COALESCE(SUM(ABS(amount_cents)), 0) FROM transactions WHERE year_month = :yearMonth AND amount_cents < 0")
        int sumExpensesForMonth(String yearMonth);

        @Query("SELECT * FROM transactions " +
            "WHERE (:yearMonth IS NULL OR year_month = :yearMonth) " +
            "AND (:categoryId IS NULL OR category_id = :categoryId) " +
            "AND (:accountId IS NULL OR account_id = :accountId) " +
            "ORDER BY booking_date DESC, id DESC")
        List<TransactionEntity> filter(String yearMonth, Long categoryId, Long accountId);
    }

    @Entity(tableName = "transactions")
    static class TransactionEntity {
        @PrimaryKey
        public long id;

        @ColumnInfo(name = "account_id")
        public long accountId;

        @ColumnInfo(name = "category_id")
        public long categoryId;

        @ColumnInfo(name = "amount_cents")
        public int amountCents;

        @ColumnInfo(name = "booking_date")
        public String bookingDate;

        @ColumnInfo(name = "year_month")
        public String yearMonth;

        TransactionEntity(long id, long accountId, long categoryId, int amountCents, String bookingDate, String yearMonth) {
            this.id = id;
            this.accountId = accountId;
            this.categoryId = categoryId;
            this.amountCents = amountCents;
            this.bookingDate = bookingDate;
            this.yearMonth = yearMonth;
        }
    }

    @Entity(tableName = "accounts")
    static class AccountEntity {
        @PrimaryKey
        public long id;
        public String name;

        AccountEntity(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Entity(tableName = "categories")
    static class CategoryEntity {
        @PrimaryKey
        public long id;
        public String name;

        CategoryEntity(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
