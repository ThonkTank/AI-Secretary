package com.secretary.features.statistics.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class CompletionDao_Impl implements CompletionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CompletionEntity> __insertionAdapterOfCompletionEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteCompletionsForTask;

  public CompletionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCompletionEntity = new EntityInsertionAdapter<CompletionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `completions` (`completion_id`,`task_id`,`completed_at`,`time_spent_minutes`,`difficulty`,`notes`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CompletionEntity entity) {
        statement.bindLong(1, entity.getCompletionId());
        statement.bindLong(2, entity.getTaskId());
        statement.bindLong(3, entity.getCompletedAt());
        statement.bindLong(4, entity.getTimeSpentMinutes());
        statement.bindLong(5, entity.getDifficulty());
        if (entity.getNotes() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getNotes());
        }
      }
    };
    this.__preparedStmtOfDeleteCompletionsForTask = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM completions WHERE task_id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertCompletion(final CompletionEntity completion,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfCompletionEntity.insertAndReturnId(completion);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteCompletionsForTask(final long taskId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteCompletionsForTask.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, taskId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteCompletionsForTask.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getCompletionsForTask(final long taskId,
      final Continuation<? super List<CompletionEntity>> $completion) {
    final String _sql = "SELECT * FROM completions WHERE task_id = ? ORDER BY completed_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, taskId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CompletionEntity>>() {
      @Override
      @NonNull
      public List<CompletionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCompletionId = CursorUtil.getColumnIndexOrThrow(_cursor, "completion_id");
          final int _cursorIndexOfTaskId = CursorUtil.getColumnIndexOrThrow(_cursor, "task_id");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completed_at");
          final int _cursorIndexOfTimeSpentMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "time_spent_minutes");
          final int _cursorIndexOfDifficulty = CursorUtil.getColumnIndexOrThrow(_cursor, "difficulty");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final List<CompletionEntity> _result = new ArrayList<CompletionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CompletionEntity _item;
            final long _tmpCompletionId;
            _tmpCompletionId = _cursor.getLong(_cursorIndexOfCompletionId);
            final long _tmpTaskId;
            _tmpTaskId = _cursor.getLong(_cursorIndexOfTaskId);
            final long _tmpCompletedAt;
            _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            final int _tmpTimeSpentMinutes;
            _tmpTimeSpentMinutes = _cursor.getInt(_cursorIndexOfTimeSpentMinutes);
            final int _tmpDifficulty;
            _tmpDifficulty = _cursor.getInt(_cursorIndexOfDifficulty);
            final String _tmpNotes;
            if (_cursor.isNull(_cursorIndexOfNotes)) {
              _tmpNotes = null;
            } else {
              _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            }
            _item = new CompletionEntity(_tmpCompletionId,_tmpTaskId,_tmpCompletedAt,_tmpTimeSpentMinutes,_tmpDifficulty,_tmpNotes);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllCompletions(final Continuation<? super List<CompletionEntity>> $completion) {
    final String _sql = "SELECT * FROM completions ORDER BY completed_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CompletionEntity>>() {
      @Override
      @NonNull
      public List<CompletionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCompletionId = CursorUtil.getColumnIndexOrThrow(_cursor, "completion_id");
          final int _cursorIndexOfTaskId = CursorUtil.getColumnIndexOrThrow(_cursor, "task_id");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completed_at");
          final int _cursorIndexOfTimeSpentMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "time_spent_minutes");
          final int _cursorIndexOfDifficulty = CursorUtil.getColumnIndexOrThrow(_cursor, "difficulty");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final List<CompletionEntity> _result = new ArrayList<CompletionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CompletionEntity _item;
            final long _tmpCompletionId;
            _tmpCompletionId = _cursor.getLong(_cursorIndexOfCompletionId);
            final long _tmpTaskId;
            _tmpTaskId = _cursor.getLong(_cursorIndexOfTaskId);
            final long _tmpCompletedAt;
            _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            final int _tmpTimeSpentMinutes;
            _tmpTimeSpentMinutes = _cursor.getInt(_cursorIndexOfTimeSpentMinutes);
            final int _tmpDifficulty;
            _tmpDifficulty = _cursor.getInt(_cursorIndexOfDifficulty);
            final String _tmpNotes;
            if (_cursor.isNull(_cursorIndexOfNotes)) {
              _tmpNotes = null;
            } else {
              _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            }
            _item = new CompletionEntity(_tmpCompletionId,_tmpTaskId,_tmpCompletedAt,_tmpTimeSpentMinutes,_tmpDifficulty,_tmpNotes);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCompletionsAfter(final long timestamp,
      final Continuation<? super List<CompletionEntity>> $completion) {
    final String _sql = "SELECT * FROM completions WHERE completed_at >= ? ORDER BY completed_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, timestamp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CompletionEntity>>() {
      @Override
      @NonNull
      public List<CompletionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCompletionId = CursorUtil.getColumnIndexOrThrow(_cursor, "completion_id");
          final int _cursorIndexOfTaskId = CursorUtil.getColumnIndexOrThrow(_cursor, "task_id");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completed_at");
          final int _cursorIndexOfTimeSpentMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "time_spent_minutes");
          final int _cursorIndexOfDifficulty = CursorUtil.getColumnIndexOrThrow(_cursor, "difficulty");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final List<CompletionEntity> _result = new ArrayList<CompletionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CompletionEntity _item;
            final long _tmpCompletionId;
            _tmpCompletionId = _cursor.getLong(_cursorIndexOfCompletionId);
            final long _tmpTaskId;
            _tmpTaskId = _cursor.getLong(_cursorIndexOfTaskId);
            final long _tmpCompletedAt;
            _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            final int _tmpTimeSpentMinutes;
            _tmpTimeSpentMinutes = _cursor.getInt(_cursorIndexOfTimeSpentMinutes);
            final int _tmpDifficulty;
            _tmpDifficulty = _cursor.getInt(_cursorIndexOfDifficulty);
            final String _tmpNotes;
            if (_cursor.isNull(_cursorIndexOfNotes)) {
              _tmpNotes = null;
            } else {
              _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            }
            _item = new CompletionEntity(_tmpCompletionId,_tmpTaskId,_tmpCompletedAt,_tmpTimeSpentMinutes,_tmpDifficulty,_tmpNotes);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAverageCompletionTime(final long taskId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT AVG(time_spent_minutes) FROM completions WHERE task_id = ? AND time_spent_minutes > 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, taskId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @Nullable
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCompletionCount(final long taskId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM completions WHERE task_id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, taskId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCompletionCountAfter(final long timestamp,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM completions WHERE completed_at >= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, timestamp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAverageDifficulty(final long taskId,
      final Continuation<? super Double> $completion) {
    final String _sql = "SELECT AVG(difficulty) FROM completions WHERE task_id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, taskId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Double>() {
      @Override
      @Nullable
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if (_cursor.moveToFirst()) {
            final Double _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getDouble(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getLatestCompletionForTask(final long taskId,
      final Continuation<? super CompletionEntity> $completion) {
    final String _sql = "SELECT * FROM completions WHERE task_id = ? ORDER BY completed_at DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, taskId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CompletionEntity>() {
      @Override
      @Nullable
      public CompletionEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCompletionId = CursorUtil.getColumnIndexOrThrow(_cursor, "completion_id");
          final int _cursorIndexOfTaskId = CursorUtil.getColumnIndexOrThrow(_cursor, "task_id");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completed_at");
          final int _cursorIndexOfTimeSpentMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "time_spent_minutes");
          final int _cursorIndexOfDifficulty = CursorUtil.getColumnIndexOrThrow(_cursor, "difficulty");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final CompletionEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpCompletionId;
            _tmpCompletionId = _cursor.getLong(_cursorIndexOfCompletionId);
            final long _tmpTaskId;
            _tmpTaskId = _cursor.getLong(_cursorIndexOfTaskId);
            final long _tmpCompletedAt;
            _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            final int _tmpTimeSpentMinutes;
            _tmpTimeSpentMinutes = _cursor.getInt(_cursorIndexOfTimeSpentMinutes);
            final int _tmpDifficulty;
            _tmpDifficulty = _cursor.getInt(_cursorIndexOfDifficulty);
            final String _tmpNotes;
            if (_cursor.isNull(_cursorIndexOfNotes)) {
              _tmpNotes = null;
            } else {
              _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            }
            _result = new CompletionEntity(_tmpCompletionId,_tmpTaskId,_tmpCompletedAt,_tmpTimeSpentMinutes,_tmpDifficulty,_tmpNotes);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getTotalTimeSpentOnTask(final long taskId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT SUM(time_spent_minutes) FROM completions WHERE task_id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, taskId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @Nullable
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
