package com.secretary.features.tasks.data;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TaskDao_Impl implements TaskDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TaskEntity> __insertionAdapterOfTaskEntity;

  private final EntityDeletionOrUpdateAdapter<TaskEntity> __deletionAdapterOfTaskEntity;

  private final EntityDeletionOrUpdateAdapter<TaskEntity> __updateAdapterOfTaskEntity;

  public TaskDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTaskEntity = new EntityInsertionAdapter<TaskEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `tasks` (`id`,`title`,`description`,`category`,`created_at`,`due_date`,`is_completed`,`priority`,`recurrence_type`,`recurrence_amount`,`recurrence_unit`,`last_completed_date`,`completions_this_period`,`current_period_start`,`current_streak`,`longest_streak`,`last_streak_date`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TaskEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        if (entity.getDescription() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getDescription());
        }
        statement.bindString(4, entity.getCategory());
        statement.bindLong(5, entity.getCreatedAt());
        statement.bindLong(6, entity.getDueDate());
        statement.bindLong(7, entity.isCompleted());
        statement.bindLong(8, entity.getPriority());
        statement.bindLong(9, entity.getRecurrenceType());
        statement.bindLong(10, entity.getRecurrenceAmount());
        statement.bindLong(11, entity.getRecurrenceUnit());
        statement.bindLong(12, entity.getLastCompletedDate());
        statement.bindLong(13, entity.getCompletionsThisPeriod());
        statement.bindLong(14, entity.getCurrentPeriodStart());
        statement.bindLong(15, entity.getCurrentStreak());
        statement.bindLong(16, entity.getLongestStreak());
        statement.bindLong(17, entity.getLastStreakDate());
      }
    };
    this.__deletionAdapterOfTaskEntity = new EntityDeletionOrUpdateAdapter<TaskEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `tasks` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TaskEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfTaskEntity = new EntityDeletionOrUpdateAdapter<TaskEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `tasks` SET `id` = ?,`title` = ?,`description` = ?,`category` = ?,`created_at` = ?,`due_date` = ?,`is_completed` = ?,`priority` = ?,`recurrence_type` = ?,`recurrence_amount` = ?,`recurrence_unit` = ?,`last_completed_date` = ?,`completions_this_period` = ?,`current_period_start` = ?,`current_streak` = ?,`longest_streak` = ?,`last_streak_date` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TaskEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        if (entity.getDescription() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getDescription());
        }
        statement.bindString(4, entity.getCategory());
        statement.bindLong(5, entity.getCreatedAt());
        statement.bindLong(6, entity.getDueDate());
        statement.bindLong(7, entity.isCompleted());
        statement.bindLong(8, entity.getPriority());
        statement.bindLong(9, entity.getRecurrenceType());
        statement.bindLong(10, entity.getRecurrenceAmount());
        statement.bindLong(11, entity.getRecurrenceUnit());
        statement.bindLong(12, entity.getLastCompletedDate());
        statement.bindLong(13, entity.getCompletionsThisPeriod());
        statement.bindLong(14, entity.getCurrentPeriodStart());
        statement.bindLong(15, entity.getCurrentStreak());
        statement.bindLong(16, entity.getLongestStreak());
        statement.bindLong(17, entity.getLastStreakDate());
        statement.bindLong(18, entity.getId());
      }
    };
  }

  @Override
  public long insertTask(final TaskEntity task) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfTaskEntity.insertAndReturnId(task);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteTask(final TaskEntity task) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfTaskEntity.handle(task);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void updateTask(final TaskEntity task) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfTaskEntity.handle(task);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public List<TaskEntity> getAllTasks() {
    final String _sql = "SELECT * FROM tasks ORDER BY is_completed ASC, priority DESC, created_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
      final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "due_date");
      final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "is_completed");
      final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
      final int _cursorIndexOfRecurrenceType = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_type");
      final int _cursorIndexOfRecurrenceAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_amount");
      final int _cursorIndexOfRecurrenceUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_unit");
      final int _cursorIndexOfLastCompletedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_completed_date");
      final int _cursorIndexOfCompletionsThisPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "completions_this_period");
      final int _cursorIndexOfCurrentPeriodStart = CursorUtil.getColumnIndexOrThrow(_cursor, "current_period_start");
      final int _cursorIndexOfCurrentStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "current_streak");
      final int _cursorIndexOfLongestStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "longest_streak");
      final int _cursorIndexOfLastStreakDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_streak_date");
      final List<TaskEntity> _result = new ArrayList<TaskEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final TaskEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpDescription;
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _tmpDescription = null;
        } else {
          _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        }
        final String _tmpCategory;
        _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpDueDate;
        _tmpDueDate = _cursor.getLong(_cursorIndexOfDueDate);
        final int _tmpIsCompleted;
        _tmpIsCompleted = _cursor.getInt(_cursorIndexOfIsCompleted);
        final int _tmpPriority;
        _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
        final int _tmpRecurrenceType;
        _tmpRecurrenceType = _cursor.getInt(_cursorIndexOfRecurrenceType);
        final int _tmpRecurrenceAmount;
        _tmpRecurrenceAmount = _cursor.getInt(_cursorIndexOfRecurrenceAmount);
        final int _tmpRecurrenceUnit;
        _tmpRecurrenceUnit = _cursor.getInt(_cursorIndexOfRecurrenceUnit);
        final long _tmpLastCompletedDate;
        _tmpLastCompletedDate = _cursor.getLong(_cursorIndexOfLastCompletedDate);
        final int _tmpCompletionsThisPeriod;
        _tmpCompletionsThisPeriod = _cursor.getInt(_cursorIndexOfCompletionsThisPeriod);
        final long _tmpCurrentPeriodStart;
        _tmpCurrentPeriodStart = _cursor.getLong(_cursorIndexOfCurrentPeriodStart);
        final int _tmpCurrentStreak;
        _tmpCurrentStreak = _cursor.getInt(_cursorIndexOfCurrentStreak);
        final int _tmpLongestStreak;
        _tmpLongestStreak = _cursor.getInt(_cursorIndexOfLongestStreak);
        final long _tmpLastStreakDate;
        _tmpLastStreakDate = _cursor.getLong(_cursorIndexOfLastStreakDate);
        _item = new TaskEntity(_tmpId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpCreatedAt,_tmpDueDate,_tmpIsCompleted,_tmpPriority,_tmpRecurrenceType,_tmpRecurrenceAmount,_tmpRecurrenceUnit,_tmpLastCompletedDate,_tmpCompletionsThisPeriod,_tmpCurrentPeriodStart,_tmpCurrentStreak,_tmpLongestStreak,_tmpLastStreakDate);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<TaskEntity> getActiveTasks() {
    final String _sql = "SELECT * FROM tasks WHERE is_completed = 0 ORDER BY priority DESC, created_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
      final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "due_date");
      final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "is_completed");
      final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
      final int _cursorIndexOfRecurrenceType = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_type");
      final int _cursorIndexOfRecurrenceAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_amount");
      final int _cursorIndexOfRecurrenceUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_unit");
      final int _cursorIndexOfLastCompletedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_completed_date");
      final int _cursorIndexOfCompletionsThisPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "completions_this_period");
      final int _cursorIndexOfCurrentPeriodStart = CursorUtil.getColumnIndexOrThrow(_cursor, "current_period_start");
      final int _cursorIndexOfCurrentStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "current_streak");
      final int _cursorIndexOfLongestStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "longest_streak");
      final int _cursorIndexOfLastStreakDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_streak_date");
      final List<TaskEntity> _result = new ArrayList<TaskEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final TaskEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpDescription;
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _tmpDescription = null;
        } else {
          _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        }
        final String _tmpCategory;
        _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpDueDate;
        _tmpDueDate = _cursor.getLong(_cursorIndexOfDueDate);
        final int _tmpIsCompleted;
        _tmpIsCompleted = _cursor.getInt(_cursorIndexOfIsCompleted);
        final int _tmpPriority;
        _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
        final int _tmpRecurrenceType;
        _tmpRecurrenceType = _cursor.getInt(_cursorIndexOfRecurrenceType);
        final int _tmpRecurrenceAmount;
        _tmpRecurrenceAmount = _cursor.getInt(_cursorIndexOfRecurrenceAmount);
        final int _tmpRecurrenceUnit;
        _tmpRecurrenceUnit = _cursor.getInt(_cursorIndexOfRecurrenceUnit);
        final long _tmpLastCompletedDate;
        _tmpLastCompletedDate = _cursor.getLong(_cursorIndexOfLastCompletedDate);
        final int _tmpCompletionsThisPeriod;
        _tmpCompletionsThisPeriod = _cursor.getInt(_cursorIndexOfCompletionsThisPeriod);
        final long _tmpCurrentPeriodStart;
        _tmpCurrentPeriodStart = _cursor.getLong(_cursorIndexOfCurrentPeriodStart);
        final int _tmpCurrentStreak;
        _tmpCurrentStreak = _cursor.getInt(_cursorIndexOfCurrentStreak);
        final int _tmpLongestStreak;
        _tmpLongestStreak = _cursor.getInt(_cursorIndexOfLongestStreak);
        final long _tmpLastStreakDate;
        _tmpLastStreakDate = _cursor.getLong(_cursorIndexOfLastStreakDate);
        _item = new TaskEntity(_tmpId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpCreatedAt,_tmpDueDate,_tmpIsCompleted,_tmpPriority,_tmpRecurrenceType,_tmpRecurrenceAmount,_tmpRecurrenceUnit,_tmpLastCompletedDate,_tmpCompletionsThisPeriod,_tmpCurrentPeriodStart,_tmpCurrentStreak,_tmpLongestStreak,_tmpLastStreakDate);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<TaskEntity> getCompletedTasks() {
    final String _sql = "SELECT * FROM tasks WHERE is_completed = 1 ORDER BY created_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
      final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "due_date");
      final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "is_completed");
      final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
      final int _cursorIndexOfRecurrenceType = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_type");
      final int _cursorIndexOfRecurrenceAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_amount");
      final int _cursorIndexOfRecurrenceUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_unit");
      final int _cursorIndexOfLastCompletedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_completed_date");
      final int _cursorIndexOfCompletionsThisPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "completions_this_period");
      final int _cursorIndexOfCurrentPeriodStart = CursorUtil.getColumnIndexOrThrow(_cursor, "current_period_start");
      final int _cursorIndexOfCurrentStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "current_streak");
      final int _cursorIndexOfLongestStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "longest_streak");
      final int _cursorIndexOfLastStreakDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_streak_date");
      final List<TaskEntity> _result = new ArrayList<TaskEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final TaskEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpDescription;
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _tmpDescription = null;
        } else {
          _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        }
        final String _tmpCategory;
        _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpDueDate;
        _tmpDueDate = _cursor.getLong(_cursorIndexOfDueDate);
        final int _tmpIsCompleted;
        _tmpIsCompleted = _cursor.getInt(_cursorIndexOfIsCompleted);
        final int _tmpPriority;
        _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
        final int _tmpRecurrenceType;
        _tmpRecurrenceType = _cursor.getInt(_cursorIndexOfRecurrenceType);
        final int _tmpRecurrenceAmount;
        _tmpRecurrenceAmount = _cursor.getInt(_cursorIndexOfRecurrenceAmount);
        final int _tmpRecurrenceUnit;
        _tmpRecurrenceUnit = _cursor.getInt(_cursorIndexOfRecurrenceUnit);
        final long _tmpLastCompletedDate;
        _tmpLastCompletedDate = _cursor.getLong(_cursorIndexOfLastCompletedDate);
        final int _tmpCompletionsThisPeriod;
        _tmpCompletionsThisPeriod = _cursor.getInt(_cursorIndexOfCompletionsThisPeriod);
        final long _tmpCurrentPeriodStart;
        _tmpCurrentPeriodStart = _cursor.getLong(_cursorIndexOfCurrentPeriodStart);
        final int _tmpCurrentStreak;
        _tmpCurrentStreak = _cursor.getInt(_cursorIndexOfCurrentStreak);
        final int _tmpLongestStreak;
        _tmpLongestStreak = _cursor.getInt(_cursorIndexOfLongestStreak);
        final long _tmpLastStreakDate;
        _tmpLastStreakDate = _cursor.getLong(_cursorIndexOfLastStreakDate);
        _item = new TaskEntity(_tmpId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpCreatedAt,_tmpDueDate,_tmpIsCompleted,_tmpPriority,_tmpRecurrenceType,_tmpRecurrenceAmount,_tmpRecurrenceUnit,_tmpLastCompletedDate,_tmpCompletionsThisPeriod,_tmpCurrentPeriodStart,_tmpCurrentStreak,_tmpLongestStreak,_tmpLastStreakDate);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public TaskEntity getTaskById(final long taskId) {
    final String _sql = "SELECT * FROM tasks WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, taskId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
      final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "due_date");
      final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "is_completed");
      final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
      final int _cursorIndexOfRecurrenceType = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_type");
      final int _cursorIndexOfRecurrenceAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_amount");
      final int _cursorIndexOfRecurrenceUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_unit");
      final int _cursorIndexOfLastCompletedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_completed_date");
      final int _cursorIndexOfCompletionsThisPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "completions_this_period");
      final int _cursorIndexOfCurrentPeriodStart = CursorUtil.getColumnIndexOrThrow(_cursor, "current_period_start");
      final int _cursorIndexOfCurrentStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "current_streak");
      final int _cursorIndexOfLongestStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "longest_streak");
      final int _cursorIndexOfLastStreakDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_streak_date");
      final TaskEntity _result;
      if (_cursor.moveToFirst()) {
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpDescription;
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _tmpDescription = null;
        } else {
          _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        }
        final String _tmpCategory;
        _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpDueDate;
        _tmpDueDate = _cursor.getLong(_cursorIndexOfDueDate);
        final int _tmpIsCompleted;
        _tmpIsCompleted = _cursor.getInt(_cursorIndexOfIsCompleted);
        final int _tmpPriority;
        _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
        final int _tmpRecurrenceType;
        _tmpRecurrenceType = _cursor.getInt(_cursorIndexOfRecurrenceType);
        final int _tmpRecurrenceAmount;
        _tmpRecurrenceAmount = _cursor.getInt(_cursorIndexOfRecurrenceAmount);
        final int _tmpRecurrenceUnit;
        _tmpRecurrenceUnit = _cursor.getInt(_cursorIndexOfRecurrenceUnit);
        final long _tmpLastCompletedDate;
        _tmpLastCompletedDate = _cursor.getLong(_cursorIndexOfLastCompletedDate);
        final int _tmpCompletionsThisPeriod;
        _tmpCompletionsThisPeriod = _cursor.getInt(_cursorIndexOfCompletionsThisPeriod);
        final long _tmpCurrentPeriodStart;
        _tmpCurrentPeriodStart = _cursor.getLong(_cursorIndexOfCurrentPeriodStart);
        final int _tmpCurrentStreak;
        _tmpCurrentStreak = _cursor.getInt(_cursorIndexOfCurrentStreak);
        final int _tmpLongestStreak;
        _tmpLongestStreak = _cursor.getInt(_cursorIndexOfLongestStreak);
        final long _tmpLastStreakDate;
        _tmpLastStreakDate = _cursor.getLong(_cursorIndexOfLastStreakDate);
        _result = new TaskEntity(_tmpId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpCreatedAt,_tmpDueDate,_tmpIsCompleted,_tmpPriority,_tmpRecurrenceType,_tmpRecurrenceAmount,_tmpRecurrenceUnit,_tmpLastCompletedDate,_tmpCompletionsThisPeriod,_tmpCurrentPeriodStart,_tmpCurrentStreak,_tmpLongestStreak,_tmpLastStreakDate);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int getTaskCount() {
    final String _sql = "SELECT COUNT(*) FROM tasks";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int getTasksCompletedToday() {
    final String _sql = "SELECT COUNT(*) FROM tasks WHERE is_completed = 1 AND DATE(last_completed_date/1000, 'unixepoch') = DATE('now')";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int getTasksCompletedLast7Days(final long sevenDaysAgo) {
    final String _sql = "SELECT COUNT(*) FROM tasks WHERE is_completed = 1 AND last_completed_date >= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sevenDaysAgo);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int getOverdueTasksCount(final long currentTime) {
    final String _sql = "SELECT COUNT(*) FROM tasks WHERE is_completed = 0 AND due_date > 0 AND due_date < ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, currentTime);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<String> getAllCategories() {
    final String _sql = "SELECT DISTINCT category FROM tasks WHERE category IS NOT NULL ORDER BY category ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final List<String> _result = new ArrayList<String>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final String _item;
        _item = _cursor.getString(0);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<TaskEntity> getTasksByCategory(final String category) {
    final String _sql = "SELECT * FROM tasks WHERE category = ? ORDER BY is_completed ASC, priority DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, category);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
      final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "due_date");
      final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "is_completed");
      final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
      final int _cursorIndexOfRecurrenceType = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_type");
      final int _cursorIndexOfRecurrenceAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_amount");
      final int _cursorIndexOfRecurrenceUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_unit");
      final int _cursorIndexOfLastCompletedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_completed_date");
      final int _cursorIndexOfCompletionsThisPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "completions_this_period");
      final int _cursorIndexOfCurrentPeriodStart = CursorUtil.getColumnIndexOrThrow(_cursor, "current_period_start");
      final int _cursorIndexOfCurrentStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "current_streak");
      final int _cursorIndexOfLongestStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "longest_streak");
      final int _cursorIndexOfLastStreakDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_streak_date");
      final List<TaskEntity> _result = new ArrayList<TaskEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final TaskEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpDescription;
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _tmpDescription = null;
        } else {
          _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        }
        final String _tmpCategory;
        _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpDueDate;
        _tmpDueDate = _cursor.getLong(_cursorIndexOfDueDate);
        final int _tmpIsCompleted;
        _tmpIsCompleted = _cursor.getInt(_cursorIndexOfIsCompleted);
        final int _tmpPriority;
        _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
        final int _tmpRecurrenceType;
        _tmpRecurrenceType = _cursor.getInt(_cursorIndexOfRecurrenceType);
        final int _tmpRecurrenceAmount;
        _tmpRecurrenceAmount = _cursor.getInt(_cursorIndexOfRecurrenceAmount);
        final int _tmpRecurrenceUnit;
        _tmpRecurrenceUnit = _cursor.getInt(_cursorIndexOfRecurrenceUnit);
        final long _tmpLastCompletedDate;
        _tmpLastCompletedDate = _cursor.getLong(_cursorIndexOfLastCompletedDate);
        final int _tmpCompletionsThisPeriod;
        _tmpCompletionsThisPeriod = _cursor.getInt(_cursorIndexOfCompletionsThisPeriod);
        final long _tmpCurrentPeriodStart;
        _tmpCurrentPeriodStart = _cursor.getLong(_cursorIndexOfCurrentPeriodStart);
        final int _tmpCurrentStreak;
        _tmpCurrentStreak = _cursor.getInt(_cursorIndexOfCurrentStreak);
        final int _tmpLongestStreak;
        _tmpLongestStreak = _cursor.getInt(_cursorIndexOfLongestStreak);
        final long _tmpLastStreakDate;
        _tmpLastStreakDate = _cursor.getLong(_cursorIndexOfLastStreakDate);
        _item = new TaskEntity(_tmpId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpCreatedAt,_tmpDueDate,_tmpIsCompleted,_tmpPriority,_tmpRecurrenceType,_tmpRecurrenceAmount,_tmpRecurrenceUnit,_tmpLastCompletedDate,_tmpCompletionsThisPeriod,_tmpCurrentPeriodStart,_tmpCurrentStreak,_tmpLongestStreak,_tmpLastStreakDate);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<TaskEntity> getRecurringTasks() {
    final String _sql = "SELECT * FROM tasks WHERE recurrence_type > 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
      final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "due_date");
      final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "is_completed");
      final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
      final int _cursorIndexOfRecurrenceType = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_type");
      final int _cursorIndexOfRecurrenceAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_amount");
      final int _cursorIndexOfRecurrenceUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_unit");
      final int _cursorIndexOfLastCompletedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_completed_date");
      final int _cursorIndexOfCompletionsThisPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "completions_this_period");
      final int _cursorIndexOfCurrentPeriodStart = CursorUtil.getColumnIndexOrThrow(_cursor, "current_period_start");
      final int _cursorIndexOfCurrentStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "current_streak");
      final int _cursorIndexOfLongestStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "longest_streak");
      final int _cursorIndexOfLastStreakDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_streak_date");
      final List<TaskEntity> _result = new ArrayList<TaskEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final TaskEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpDescription;
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _tmpDescription = null;
        } else {
          _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        }
        final String _tmpCategory;
        _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpDueDate;
        _tmpDueDate = _cursor.getLong(_cursorIndexOfDueDate);
        final int _tmpIsCompleted;
        _tmpIsCompleted = _cursor.getInt(_cursorIndexOfIsCompleted);
        final int _tmpPriority;
        _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
        final int _tmpRecurrenceType;
        _tmpRecurrenceType = _cursor.getInt(_cursorIndexOfRecurrenceType);
        final int _tmpRecurrenceAmount;
        _tmpRecurrenceAmount = _cursor.getInt(_cursorIndexOfRecurrenceAmount);
        final int _tmpRecurrenceUnit;
        _tmpRecurrenceUnit = _cursor.getInt(_cursorIndexOfRecurrenceUnit);
        final long _tmpLastCompletedDate;
        _tmpLastCompletedDate = _cursor.getLong(_cursorIndexOfLastCompletedDate);
        final int _tmpCompletionsThisPeriod;
        _tmpCompletionsThisPeriod = _cursor.getInt(_cursorIndexOfCompletionsThisPeriod);
        final long _tmpCurrentPeriodStart;
        _tmpCurrentPeriodStart = _cursor.getLong(_cursorIndexOfCurrentPeriodStart);
        final int _tmpCurrentStreak;
        _tmpCurrentStreak = _cursor.getInt(_cursorIndexOfCurrentStreak);
        final int _tmpLongestStreak;
        _tmpLongestStreak = _cursor.getInt(_cursorIndexOfLongestStreak);
        final long _tmpLastStreakDate;
        _tmpLastStreakDate = _cursor.getLong(_cursorIndexOfLastStreakDate);
        _item = new TaskEntity(_tmpId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpCreatedAt,_tmpDueDate,_tmpIsCompleted,_tmpPriority,_tmpRecurrenceType,_tmpRecurrenceAmount,_tmpRecurrenceUnit,_tmpLastCompletedDate,_tmpCompletionsThisPeriod,_tmpCurrentPeriodStart,_tmpCurrentStreak,_tmpLongestStreak,_tmpLastStreakDate);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<TaskEntity> getDueIntervalTasks(final long currentTime) {
    final String _sql = "SELECT * FROM tasks WHERE recurrence_type = 1 AND is_completed = 1 AND due_date <= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, currentTime);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
      final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "due_date");
      final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "is_completed");
      final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
      final int _cursorIndexOfRecurrenceType = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_type");
      final int _cursorIndexOfRecurrenceAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_amount");
      final int _cursorIndexOfRecurrenceUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_unit");
      final int _cursorIndexOfLastCompletedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_completed_date");
      final int _cursorIndexOfCompletionsThisPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "completions_this_period");
      final int _cursorIndexOfCurrentPeriodStart = CursorUtil.getColumnIndexOrThrow(_cursor, "current_period_start");
      final int _cursorIndexOfCurrentStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "current_streak");
      final int _cursorIndexOfLongestStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "longest_streak");
      final int _cursorIndexOfLastStreakDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_streak_date");
      final List<TaskEntity> _result = new ArrayList<TaskEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final TaskEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpDescription;
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _tmpDescription = null;
        } else {
          _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        }
        final String _tmpCategory;
        _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpDueDate;
        _tmpDueDate = _cursor.getLong(_cursorIndexOfDueDate);
        final int _tmpIsCompleted;
        _tmpIsCompleted = _cursor.getInt(_cursorIndexOfIsCompleted);
        final int _tmpPriority;
        _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
        final int _tmpRecurrenceType;
        _tmpRecurrenceType = _cursor.getInt(_cursorIndexOfRecurrenceType);
        final int _tmpRecurrenceAmount;
        _tmpRecurrenceAmount = _cursor.getInt(_cursorIndexOfRecurrenceAmount);
        final int _tmpRecurrenceUnit;
        _tmpRecurrenceUnit = _cursor.getInt(_cursorIndexOfRecurrenceUnit);
        final long _tmpLastCompletedDate;
        _tmpLastCompletedDate = _cursor.getLong(_cursorIndexOfLastCompletedDate);
        final int _tmpCompletionsThisPeriod;
        _tmpCompletionsThisPeriod = _cursor.getInt(_cursorIndexOfCompletionsThisPeriod);
        final long _tmpCurrentPeriodStart;
        _tmpCurrentPeriodStart = _cursor.getLong(_cursorIndexOfCurrentPeriodStart);
        final int _tmpCurrentStreak;
        _tmpCurrentStreak = _cursor.getInt(_cursorIndexOfCurrentStreak);
        final int _tmpLongestStreak;
        _tmpLongestStreak = _cursor.getInt(_cursorIndexOfLongestStreak);
        final long _tmpLastStreakDate;
        _tmpLastStreakDate = _cursor.getLong(_cursorIndexOfLastStreakDate);
        _item = new TaskEntity(_tmpId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpCreatedAt,_tmpDueDate,_tmpIsCompleted,_tmpPriority,_tmpRecurrenceType,_tmpRecurrenceAmount,_tmpRecurrenceUnit,_tmpLastCompletedDate,_tmpCompletionsThisPeriod,_tmpCurrentPeriodStart,_tmpCurrentStreak,_tmpLongestStreak,_tmpLastStreakDate);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<TaskEntity> getFrequencyTasks() {
    final String _sql = "SELECT * FROM tasks WHERE recurrence_type = 2";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
      final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "due_date");
      final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "is_completed");
      final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
      final int _cursorIndexOfRecurrenceType = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_type");
      final int _cursorIndexOfRecurrenceAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_amount");
      final int _cursorIndexOfRecurrenceUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_unit");
      final int _cursorIndexOfLastCompletedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_completed_date");
      final int _cursorIndexOfCompletionsThisPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "completions_this_period");
      final int _cursorIndexOfCurrentPeriodStart = CursorUtil.getColumnIndexOrThrow(_cursor, "current_period_start");
      final int _cursorIndexOfCurrentStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "current_streak");
      final int _cursorIndexOfLongestStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "longest_streak");
      final int _cursorIndexOfLastStreakDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_streak_date");
      final List<TaskEntity> _result = new ArrayList<TaskEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final TaskEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpDescription;
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _tmpDescription = null;
        } else {
          _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        }
        final String _tmpCategory;
        _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpDueDate;
        _tmpDueDate = _cursor.getLong(_cursorIndexOfDueDate);
        final int _tmpIsCompleted;
        _tmpIsCompleted = _cursor.getInt(_cursorIndexOfIsCompleted);
        final int _tmpPriority;
        _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
        final int _tmpRecurrenceType;
        _tmpRecurrenceType = _cursor.getInt(_cursorIndexOfRecurrenceType);
        final int _tmpRecurrenceAmount;
        _tmpRecurrenceAmount = _cursor.getInt(_cursorIndexOfRecurrenceAmount);
        final int _tmpRecurrenceUnit;
        _tmpRecurrenceUnit = _cursor.getInt(_cursorIndexOfRecurrenceUnit);
        final long _tmpLastCompletedDate;
        _tmpLastCompletedDate = _cursor.getLong(_cursorIndexOfLastCompletedDate);
        final int _tmpCompletionsThisPeriod;
        _tmpCompletionsThisPeriod = _cursor.getInt(_cursorIndexOfCompletionsThisPeriod);
        final long _tmpCurrentPeriodStart;
        _tmpCurrentPeriodStart = _cursor.getLong(_cursorIndexOfCurrentPeriodStart);
        final int _tmpCurrentStreak;
        _tmpCurrentStreak = _cursor.getInt(_cursorIndexOfCurrentStreak);
        final int _tmpLongestStreak;
        _tmpLongestStreak = _cursor.getInt(_cursorIndexOfLongestStreak);
        final long _tmpLastStreakDate;
        _tmpLastStreakDate = _cursor.getLong(_cursorIndexOfLastStreakDate);
        _item = new TaskEntity(_tmpId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpCreatedAt,_tmpDueDate,_tmpIsCompleted,_tmpPriority,_tmpRecurrenceType,_tmpRecurrenceAmount,_tmpRecurrenceUnit,_tmpLastCompletedDate,_tmpCompletionsThisPeriod,_tmpCurrentPeriodStart,_tmpCurrentStreak,_tmpLongestStreak,_tmpLastStreakDate);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<TaskEntity> getTasksWithStreaks() {
    final String _sql = "SELECT * FROM tasks WHERE current_streak > 0 ORDER BY current_streak DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
      final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "due_date");
      final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "is_completed");
      final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
      final int _cursorIndexOfRecurrenceType = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_type");
      final int _cursorIndexOfRecurrenceAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_amount");
      final int _cursorIndexOfRecurrenceUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_unit");
      final int _cursorIndexOfLastCompletedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_completed_date");
      final int _cursorIndexOfCompletionsThisPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "completions_this_period");
      final int _cursorIndexOfCurrentPeriodStart = CursorUtil.getColumnIndexOrThrow(_cursor, "current_period_start");
      final int _cursorIndexOfCurrentStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "current_streak");
      final int _cursorIndexOfLongestStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "longest_streak");
      final int _cursorIndexOfLastStreakDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_streak_date");
      final List<TaskEntity> _result = new ArrayList<TaskEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final TaskEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpDescription;
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _tmpDescription = null;
        } else {
          _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        }
        final String _tmpCategory;
        _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpDueDate;
        _tmpDueDate = _cursor.getLong(_cursorIndexOfDueDate);
        final int _tmpIsCompleted;
        _tmpIsCompleted = _cursor.getInt(_cursorIndexOfIsCompleted);
        final int _tmpPriority;
        _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
        final int _tmpRecurrenceType;
        _tmpRecurrenceType = _cursor.getInt(_cursorIndexOfRecurrenceType);
        final int _tmpRecurrenceAmount;
        _tmpRecurrenceAmount = _cursor.getInt(_cursorIndexOfRecurrenceAmount);
        final int _tmpRecurrenceUnit;
        _tmpRecurrenceUnit = _cursor.getInt(_cursorIndexOfRecurrenceUnit);
        final long _tmpLastCompletedDate;
        _tmpLastCompletedDate = _cursor.getLong(_cursorIndexOfLastCompletedDate);
        final int _tmpCompletionsThisPeriod;
        _tmpCompletionsThisPeriod = _cursor.getInt(_cursorIndexOfCompletionsThisPeriod);
        final long _tmpCurrentPeriodStart;
        _tmpCurrentPeriodStart = _cursor.getLong(_cursorIndexOfCurrentPeriodStart);
        final int _tmpCurrentStreak;
        _tmpCurrentStreak = _cursor.getInt(_cursorIndexOfCurrentStreak);
        final int _tmpLongestStreak;
        _tmpLongestStreak = _cursor.getInt(_cursorIndexOfLongestStreak);
        final long _tmpLastStreakDate;
        _tmpLastStreakDate = _cursor.getLong(_cursorIndexOfLastStreakDate);
        _item = new TaskEntity(_tmpId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpCreatedAt,_tmpDueDate,_tmpIsCompleted,_tmpPriority,_tmpRecurrenceType,_tmpRecurrenceAmount,_tmpRecurrenceUnit,_tmpLastCompletedDate,_tmpCompletionsThisPeriod,_tmpCurrentPeriodStart,_tmpCurrentStreak,_tmpLongestStreak,_tmpLastStreakDate);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public TaskEntity getTaskWithLongestStreak() {
    final String _sql = "SELECT * FROM tasks ORDER BY longest_streak DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
      final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "due_date");
      final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "is_completed");
      final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
      final int _cursorIndexOfRecurrenceType = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_type");
      final int _cursorIndexOfRecurrenceAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_amount");
      final int _cursorIndexOfRecurrenceUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_unit");
      final int _cursorIndexOfLastCompletedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_completed_date");
      final int _cursorIndexOfCompletionsThisPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "completions_this_period");
      final int _cursorIndexOfCurrentPeriodStart = CursorUtil.getColumnIndexOrThrow(_cursor, "current_period_start");
      final int _cursorIndexOfCurrentStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "current_streak");
      final int _cursorIndexOfLongestStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "longest_streak");
      final int _cursorIndexOfLastStreakDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_streak_date");
      final TaskEntity _result;
      if (_cursor.moveToFirst()) {
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpDescription;
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _tmpDescription = null;
        } else {
          _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        }
        final String _tmpCategory;
        _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpDueDate;
        _tmpDueDate = _cursor.getLong(_cursorIndexOfDueDate);
        final int _tmpIsCompleted;
        _tmpIsCompleted = _cursor.getInt(_cursorIndexOfIsCompleted);
        final int _tmpPriority;
        _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
        final int _tmpRecurrenceType;
        _tmpRecurrenceType = _cursor.getInt(_cursorIndexOfRecurrenceType);
        final int _tmpRecurrenceAmount;
        _tmpRecurrenceAmount = _cursor.getInt(_cursorIndexOfRecurrenceAmount);
        final int _tmpRecurrenceUnit;
        _tmpRecurrenceUnit = _cursor.getInt(_cursorIndexOfRecurrenceUnit);
        final long _tmpLastCompletedDate;
        _tmpLastCompletedDate = _cursor.getLong(_cursorIndexOfLastCompletedDate);
        final int _tmpCompletionsThisPeriod;
        _tmpCompletionsThisPeriod = _cursor.getInt(_cursorIndexOfCompletionsThisPeriod);
        final long _tmpCurrentPeriodStart;
        _tmpCurrentPeriodStart = _cursor.getLong(_cursorIndexOfCurrentPeriodStart);
        final int _tmpCurrentStreak;
        _tmpCurrentStreak = _cursor.getInt(_cursorIndexOfCurrentStreak);
        final int _tmpLongestStreak;
        _tmpLongestStreak = _cursor.getInt(_cursorIndexOfLongestStreak);
        final long _tmpLastStreakDate;
        _tmpLastStreakDate = _cursor.getLong(_cursorIndexOfLastStreakDate);
        _result = new TaskEntity(_tmpId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpCreatedAt,_tmpDueDate,_tmpIsCompleted,_tmpPriority,_tmpRecurrenceType,_tmpRecurrenceAmount,_tmpRecurrenceUnit,_tmpLastCompletedDate,_tmpCompletionsThisPeriod,_tmpCurrentPeriodStart,_tmpCurrentStreak,_tmpLongestStreak,_tmpLastStreakDate);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<TaskEntity> getTasksByPriority(final int priority) {
    final String _sql = "SELECT * FROM tasks WHERE priority = ? AND is_completed = 0 ORDER BY created_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, priority);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
      final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "due_date");
      final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "is_completed");
      final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
      final int _cursorIndexOfRecurrenceType = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_type");
      final int _cursorIndexOfRecurrenceAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_amount");
      final int _cursorIndexOfRecurrenceUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_unit");
      final int _cursorIndexOfLastCompletedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_completed_date");
      final int _cursorIndexOfCompletionsThisPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "completions_this_period");
      final int _cursorIndexOfCurrentPeriodStart = CursorUtil.getColumnIndexOrThrow(_cursor, "current_period_start");
      final int _cursorIndexOfCurrentStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "current_streak");
      final int _cursorIndexOfLongestStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "longest_streak");
      final int _cursorIndexOfLastStreakDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_streak_date");
      final List<TaskEntity> _result = new ArrayList<TaskEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final TaskEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpDescription;
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _tmpDescription = null;
        } else {
          _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        }
        final String _tmpCategory;
        _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpDueDate;
        _tmpDueDate = _cursor.getLong(_cursorIndexOfDueDate);
        final int _tmpIsCompleted;
        _tmpIsCompleted = _cursor.getInt(_cursorIndexOfIsCompleted);
        final int _tmpPriority;
        _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
        final int _tmpRecurrenceType;
        _tmpRecurrenceType = _cursor.getInt(_cursorIndexOfRecurrenceType);
        final int _tmpRecurrenceAmount;
        _tmpRecurrenceAmount = _cursor.getInt(_cursorIndexOfRecurrenceAmount);
        final int _tmpRecurrenceUnit;
        _tmpRecurrenceUnit = _cursor.getInt(_cursorIndexOfRecurrenceUnit);
        final long _tmpLastCompletedDate;
        _tmpLastCompletedDate = _cursor.getLong(_cursorIndexOfLastCompletedDate);
        final int _tmpCompletionsThisPeriod;
        _tmpCompletionsThisPeriod = _cursor.getInt(_cursorIndexOfCompletionsThisPeriod);
        final long _tmpCurrentPeriodStart;
        _tmpCurrentPeriodStart = _cursor.getLong(_cursorIndexOfCurrentPeriodStart);
        final int _tmpCurrentStreak;
        _tmpCurrentStreak = _cursor.getInt(_cursorIndexOfCurrentStreak);
        final int _tmpLongestStreak;
        _tmpLongestStreak = _cursor.getInt(_cursorIndexOfLongestStreak);
        final long _tmpLastStreakDate;
        _tmpLastStreakDate = _cursor.getLong(_cursorIndexOfLastStreakDate);
        _item = new TaskEntity(_tmpId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpCreatedAt,_tmpDueDate,_tmpIsCompleted,_tmpPriority,_tmpRecurrenceType,_tmpRecurrenceAmount,_tmpRecurrenceUnit,_tmpLastCompletedDate,_tmpCompletionsThisPeriod,_tmpCurrentPeriodStart,_tmpCurrentStreak,_tmpLongestStreak,_tmpLastStreakDate);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<TaskEntity> getHighPriorityTasks() {
    final String _sql = "SELECT * FROM tasks WHERE is_completed = 0 AND priority >= 2 ORDER BY priority DESC, due_date ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
      final int _cursorIndexOfDueDate = CursorUtil.getColumnIndexOrThrow(_cursor, "due_date");
      final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "is_completed");
      final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
      final int _cursorIndexOfRecurrenceType = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_type");
      final int _cursorIndexOfRecurrenceAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_amount");
      final int _cursorIndexOfRecurrenceUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "recurrence_unit");
      final int _cursorIndexOfLastCompletedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_completed_date");
      final int _cursorIndexOfCompletionsThisPeriod = CursorUtil.getColumnIndexOrThrow(_cursor, "completions_this_period");
      final int _cursorIndexOfCurrentPeriodStart = CursorUtil.getColumnIndexOrThrow(_cursor, "current_period_start");
      final int _cursorIndexOfCurrentStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "current_streak");
      final int _cursorIndexOfLongestStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "longest_streak");
      final int _cursorIndexOfLastStreakDate = CursorUtil.getColumnIndexOrThrow(_cursor, "last_streak_date");
      final List<TaskEntity> _result = new ArrayList<TaskEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final TaskEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpDescription;
        if (_cursor.isNull(_cursorIndexOfDescription)) {
          _tmpDescription = null;
        } else {
          _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        }
        final String _tmpCategory;
        _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final long _tmpDueDate;
        _tmpDueDate = _cursor.getLong(_cursorIndexOfDueDate);
        final int _tmpIsCompleted;
        _tmpIsCompleted = _cursor.getInt(_cursorIndexOfIsCompleted);
        final int _tmpPriority;
        _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
        final int _tmpRecurrenceType;
        _tmpRecurrenceType = _cursor.getInt(_cursorIndexOfRecurrenceType);
        final int _tmpRecurrenceAmount;
        _tmpRecurrenceAmount = _cursor.getInt(_cursorIndexOfRecurrenceAmount);
        final int _tmpRecurrenceUnit;
        _tmpRecurrenceUnit = _cursor.getInt(_cursorIndexOfRecurrenceUnit);
        final long _tmpLastCompletedDate;
        _tmpLastCompletedDate = _cursor.getLong(_cursorIndexOfLastCompletedDate);
        final int _tmpCompletionsThisPeriod;
        _tmpCompletionsThisPeriod = _cursor.getInt(_cursorIndexOfCompletionsThisPeriod);
        final long _tmpCurrentPeriodStart;
        _tmpCurrentPeriodStart = _cursor.getLong(_cursorIndexOfCurrentPeriodStart);
        final int _tmpCurrentStreak;
        _tmpCurrentStreak = _cursor.getInt(_cursorIndexOfCurrentStreak);
        final int _tmpLongestStreak;
        _tmpLongestStreak = _cursor.getInt(_cursorIndexOfLongestStreak);
        final long _tmpLastStreakDate;
        _tmpLastStreakDate = _cursor.getLong(_cursorIndexOfLastStreakDate);
        _item = new TaskEntity(_tmpId,_tmpTitle,_tmpDescription,_tmpCategory,_tmpCreatedAt,_tmpDueDate,_tmpIsCompleted,_tmpPriority,_tmpRecurrenceType,_tmpRecurrenceAmount,_tmpRecurrenceUnit,_tmpLastCompletedDate,_tmpCompletionsThisPeriod,_tmpCurrentPeriodStart,_tmpCurrentStreak,_tmpLongestStreak,_tmpLastStreakDate);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
