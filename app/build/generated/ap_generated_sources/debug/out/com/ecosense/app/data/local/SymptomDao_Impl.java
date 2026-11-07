package com.ecosense.app.data.local;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SymptomDao_Impl implements SymptomDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SymptomEntry> __insertionAdapterOfSymptomEntry;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  public SymptomDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSymptomEntry = new EntityInsertionAdapter<SymptomEntry>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `symptom_entries` (`id`,`timestamp`,`type`,`severity`,`note`,`aqi`,`pm25`,`temperature`,`humidity`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final SymptomEntry entity) {
        statement.bindLong(1, entity.id);
        statement.bindLong(2, entity.timestamp);
        statement.bindLong(3, entity.type);
        statement.bindLong(4, entity.severity);
        if (entity.note == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.note);
        }
        statement.bindLong(6, entity.aqi);
        statement.bindDouble(7, entity.pm25);
        statement.bindDouble(8, entity.temperature);
        statement.bindDouble(9, entity.humidity);
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM symptom_entries WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public long insert(final SymptomEntry entry) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfSymptomEntry.insertAndReturnId(entry);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteById(final long id) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteById.release(_stmt);
    }
  }

  @Override
  public LiveData<List<SymptomEntry>> observeAll() {
    final String _sql = "SELECT * FROM symptom_entries ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"symptom_entries"}, false, new Callable<List<SymptomEntry>>() {
      @Override
      @Nullable
      public List<SymptomEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfSeverity = CursorUtil.getColumnIndexOrThrow(_cursor, "severity");
          final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
          final int _cursorIndexOfAqi = CursorUtil.getColumnIndexOrThrow(_cursor, "aqi");
          final int _cursorIndexOfPm25 = CursorUtil.getColumnIndexOrThrow(_cursor, "pm25");
          final int _cursorIndexOfTemperature = CursorUtil.getColumnIndexOrThrow(_cursor, "temperature");
          final int _cursorIndexOfHumidity = CursorUtil.getColumnIndexOrThrow(_cursor, "humidity");
          final List<SymptomEntry> _result = new ArrayList<SymptomEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SymptomEntry _item;
            _item = new SymptomEntry();
            _item.id = _cursor.getLong(_cursorIndexOfId);
            _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item.type = _cursor.getInt(_cursorIndexOfType);
            _item.severity = _cursor.getInt(_cursorIndexOfSeverity);
            if (_cursor.isNull(_cursorIndexOfNote)) {
              _item.note = null;
            } else {
              _item.note = _cursor.getString(_cursorIndexOfNote);
            }
            _item.aqi = _cursor.getInt(_cursorIndexOfAqi);
            _item.pm25 = _cursor.getDouble(_cursorIndexOfPm25);
            _item.temperature = _cursor.getDouble(_cursorIndexOfTemperature);
            _item.humidity = _cursor.getDouble(_cursorIndexOfHumidity);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public List<SymptomEntry> getAllAsc() {
    final String _sql = "SELECT * FROM symptom_entries ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfSeverity = CursorUtil.getColumnIndexOrThrow(_cursor, "severity");
      final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
      final int _cursorIndexOfAqi = CursorUtil.getColumnIndexOrThrow(_cursor, "aqi");
      final int _cursorIndexOfPm25 = CursorUtil.getColumnIndexOrThrow(_cursor, "pm25");
      final int _cursorIndexOfTemperature = CursorUtil.getColumnIndexOrThrow(_cursor, "temperature");
      final int _cursorIndexOfHumidity = CursorUtil.getColumnIndexOrThrow(_cursor, "humidity");
      final List<SymptomEntry> _result = new ArrayList<SymptomEntry>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final SymptomEntry _item;
        _item = new SymptomEntry();
        _item.id = _cursor.getLong(_cursorIndexOfId);
        _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        _item.type = _cursor.getInt(_cursorIndexOfType);
        _item.severity = _cursor.getInt(_cursorIndexOfSeverity);
        if (_cursor.isNull(_cursorIndexOfNote)) {
          _item.note = null;
        } else {
          _item.note = _cursor.getString(_cursorIndexOfNote);
        }
        _item.aqi = _cursor.getInt(_cursorIndexOfAqi);
        _item.pm25 = _cursor.getDouble(_cursorIndexOfPm25);
        _item.temperature = _cursor.getDouble(_cursorIndexOfTemperature);
        _item.humidity = _cursor.getDouble(_cursorIndexOfHumidity);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<SymptomEntry> getAllDesc() {
    final String _sql = "SELECT * FROM symptom_entries ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
      final int _cursorIndexOfSeverity = CursorUtil.getColumnIndexOrThrow(_cursor, "severity");
      final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
      final int _cursorIndexOfAqi = CursorUtil.getColumnIndexOrThrow(_cursor, "aqi");
      final int _cursorIndexOfPm25 = CursorUtil.getColumnIndexOrThrow(_cursor, "pm25");
      final int _cursorIndexOfTemperature = CursorUtil.getColumnIndexOrThrow(_cursor, "temperature");
      final int _cursorIndexOfHumidity = CursorUtil.getColumnIndexOrThrow(_cursor, "humidity");
      final List<SymptomEntry> _result = new ArrayList<SymptomEntry>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final SymptomEntry _item;
        _item = new SymptomEntry();
        _item.id = _cursor.getLong(_cursorIndexOfId);
        _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        _item.type = _cursor.getInt(_cursorIndexOfType);
        _item.severity = _cursor.getInt(_cursorIndexOfSeverity);
        if (_cursor.isNull(_cursorIndexOfNote)) {
          _item.note = null;
        } else {
          _item.note = _cursor.getString(_cursorIndexOfNote);
        }
        _item.aqi = _cursor.getInt(_cursorIndexOfAqi);
        _item.pm25 = _cursor.getDouble(_cursorIndexOfPm25);
        _item.temperature = _cursor.getDouble(_cursorIndexOfTemperature);
        _item.humidity = _cursor.getDouble(_cursorIndexOfHumidity);
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
