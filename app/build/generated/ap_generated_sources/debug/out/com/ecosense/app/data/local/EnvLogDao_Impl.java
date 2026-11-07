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
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class EnvLogDao_Impl implements EnvLogDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<EnvLog> __insertionAdapterOfEnvLog;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public EnvLogDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfEnvLog = new EntityInsertionAdapter<EnvLog>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `env_logs` (`id`,`timestamp`,`temperature`,`humidity`,`pm25`,`pm10`,`aqi`,`synced`,`source`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement, final EnvLog entity) {
        statement.bindLong(1, entity.id);
        statement.bindLong(2, entity.timestamp);
        statement.bindDouble(3, entity.temperature);
        statement.bindDouble(4, entity.humidity);
        statement.bindDouble(5, entity.pm25);
        statement.bindDouble(6, entity.pm10);
        statement.bindLong(7, entity.aqi);
        final int _tmp = entity.synced ? 1 : 0;
        statement.bindLong(8, _tmp);
        if (entity.source == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.source);
        }
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM env_logs";
        return _query;
      }
    };
  }

  @Override
  public long insert(final EnvLog log) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfEnvLog.insertAndReturnId(log);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void clearAll() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfClearAll.release(_stmt);
    }
  }

  @Override
  public LiveData<List<EnvLog>> observeAll() {
    final String _sql = "SELECT * FROM env_logs ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"env_logs"}, false, new Callable<List<EnvLog>>() {
      @Override
      @Nullable
      public List<EnvLog> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfTemperature = CursorUtil.getColumnIndexOrThrow(_cursor, "temperature");
          final int _cursorIndexOfHumidity = CursorUtil.getColumnIndexOrThrow(_cursor, "humidity");
          final int _cursorIndexOfPm25 = CursorUtil.getColumnIndexOrThrow(_cursor, "pm25");
          final int _cursorIndexOfPm10 = CursorUtil.getColumnIndexOrThrow(_cursor, "pm10");
          final int _cursorIndexOfAqi = CursorUtil.getColumnIndexOrThrow(_cursor, "aqi");
          final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final List<EnvLog> _result = new ArrayList<EnvLog>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EnvLog _item;
            _item = new EnvLog();
            _item.id = _cursor.getLong(_cursorIndexOfId);
            _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item.temperature = _cursor.getDouble(_cursorIndexOfTemperature);
            _item.humidity = _cursor.getDouble(_cursorIndexOfHumidity);
            _item.pm25 = _cursor.getDouble(_cursorIndexOfPm25);
            _item.pm10 = _cursor.getDouble(_cursorIndexOfPm10);
            _item.aqi = _cursor.getInt(_cursorIndexOfAqi);
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSynced);
            _item.synced = _tmp != 0;
            if (_cursor.isNull(_cursorIndexOfSource)) {
              _item.source = null;
            } else {
              _item.source = _cursor.getString(_cursorIndexOfSource);
            }
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
  public Cursor getAllCursor() {
    final String _sql = "SELECT * FROM env_logs ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final Cursor _tmpResult = __db.query(_statement);
    return _tmpResult;
  }

  @Override
  public EnvLog getLatest() {
    final String _sql = "SELECT * FROM env_logs ORDER BY timestamp DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfTemperature = CursorUtil.getColumnIndexOrThrow(_cursor, "temperature");
      final int _cursorIndexOfHumidity = CursorUtil.getColumnIndexOrThrow(_cursor, "humidity");
      final int _cursorIndexOfPm25 = CursorUtil.getColumnIndexOrThrow(_cursor, "pm25");
      final int _cursorIndexOfPm10 = CursorUtil.getColumnIndexOrThrow(_cursor, "pm10");
      final int _cursorIndexOfAqi = CursorUtil.getColumnIndexOrThrow(_cursor, "aqi");
      final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
      final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
      final EnvLog _result;
      if (_cursor.moveToFirst()) {
        _result = new EnvLog();
        _result.id = _cursor.getLong(_cursorIndexOfId);
        _result.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        _result.temperature = _cursor.getDouble(_cursorIndexOfTemperature);
        _result.humidity = _cursor.getDouble(_cursorIndexOfHumidity);
        _result.pm25 = _cursor.getDouble(_cursorIndexOfPm25);
        _result.pm10 = _cursor.getDouble(_cursorIndexOfPm10);
        _result.aqi = _cursor.getInt(_cursorIndexOfAqi);
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfSynced);
        _result.synced = _tmp != 0;
        if (_cursor.isNull(_cursorIndexOfSource)) {
          _result.source = null;
        } else {
          _result.source = _cursor.getString(_cursorIndexOfSource);
        }
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
  public List<EnvLog> getRecent(final int n) {
    final String _sql = "SELECT * FROM env_logs ORDER BY timestamp DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, n);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfTemperature = CursorUtil.getColumnIndexOrThrow(_cursor, "temperature");
      final int _cursorIndexOfHumidity = CursorUtil.getColumnIndexOrThrow(_cursor, "humidity");
      final int _cursorIndexOfPm25 = CursorUtil.getColumnIndexOrThrow(_cursor, "pm25");
      final int _cursorIndexOfPm10 = CursorUtil.getColumnIndexOrThrow(_cursor, "pm10");
      final int _cursorIndexOfAqi = CursorUtil.getColumnIndexOrThrow(_cursor, "aqi");
      final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
      final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
      final List<EnvLog> _result = new ArrayList<EnvLog>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final EnvLog _item;
        _item = new EnvLog();
        _item.id = _cursor.getLong(_cursorIndexOfId);
        _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        _item.temperature = _cursor.getDouble(_cursorIndexOfTemperature);
        _item.humidity = _cursor.getDouble(_cursorIndexOfHumidity);
        _item.pm25 = _cursor.getDouble(_cursorIndexOfPm25);
        _item.pm10 = _cursor.getDouble(_cursorIndexOfPm10);
        _item.aqi = _cursor.getInt(_cursorIndexOfAqi);
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfSynced);
        _item.synced = _tmp != 0;
        if (_cursor.isNull(_cursorIndexOfSource)) {
          _item.source = null;
        } else {
          _item.source = _cursor.getString(_cursorIndexOfSource);
        }
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<EnvLog> since(final long from) {
    final String _sql = "SELECT * FROM env_logs WHERE timestamp >= ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, from);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfTemperature = CursorUtil.getColumnIndexOrThrow(_cursor, "temperature");
      final int _cursorIndexOfHumidity = CursorUtil.getColumnIndexOrThrow(_cursor, "humidity");
      final int _cursorIndexOfPm25 = CursorUtil.getColumnIndexOrThrow(_cursor, "pm25");
      final int _cursorIndexOfPm10 = CursorUtil.getColumnIndexOrThrow(_cursor, "pm10");
      final int _cursorIndexOfAqi = CursorUtil.getColumnIndexOrThrow(_cursor, "aqi");
      final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
      final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
      final List<EnvLog> _result = new ArrayList<EnvLog>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final EnvLog _item;
        _item = new EnvLog();
        _item.id = _cursor.getLong(_cursorIndexOfId);
        _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        _item.temperature = _cursor.getDouble(_cursorIndexOfTemperature);
        _item.humidity = _cursor.getDouble(_cursorIndexOfHumidity);
        _item.pm25 = _cursor.getDouble(_cursorIndexOfPm25);
        _item.pm10 = _cursor.getDouble(_cursorIndexOfPm10);
        _item.aqi = _cursor.getInt(_cursorIndexOfAqi);
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfSynced);
        _item.synced = _tmp != 0;
        if (_cursor.isNull(_cursorIndexOfSource)) {
          _item.source = null;
        } else {
          _item.source = _cursor.getString(_cursorIndexOfSource);
        }
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<EnvLog> getUnsynced() {
    final String _sql = "SELECT * FROM env_logs WHERE synced = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfTemperature = CursorUtil.getColumnIndexOrThrow(_cursor, "temperature");
      final int _cursorIndexOfHumidity = CursorUtil.getColumnIndexOrThrow(_cursor, "humidity");
      final int _cursorIndexOfPm25 = CursorUtil.getColumnIndexOrThrow(_cursor, "pm25");
      final int _cursorIndexOfPm10 = CursorUtil.getColumnIndexOrThrow(_cursor, "pm10");
      final int _cursorIndexOfAqi = CursorUtil.getColumnIndexOrThrow(_cursor, "aqi");
      final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
      final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
      final List<EnvLog> _result = new ArrayList<EnvLog>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final EnvLog _item;
        _item = new EnvLog();
        _item.id = _cursor.getLong(_cursorIndexOfId);
        _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        _item.temperature = _cursor.getDouble(_cursorIndexOfTemperature);
        _item.humidity = _cursor.getDouble(_cursorIndexOfHumidity);
        _item.pm25 = _cursor.getDouble(_cursorIndexOfPm25);
        _item.pm10 = _cursor.getDouble(_cursorIndexOfPm10);
        _item.aqi = _cursor.getInt(_cursorIndexOfAqi);
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfSynced);
        _item.synced = _tmp != 0;
        if (_cursor.isNull(_cursorIndexOfSource)) {
          _item.source = null;
        } else {
          _item.source = _cursor.getString(_cursorIndexOfSource);
        }
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int countByTimestamp(final long ts) {
    final String _sql = "SELECT COUNT(*) FROM env_logs WHERE timestamp = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, ts);
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
  public void markSynced(final List<Long> ids) {
    __db.assertNotSuspendingTransaction();
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("UPDATE env_logs SET synced = 1 WHERE id IN (");
    final int _inputSize = ids == null ? 1 : ids.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
    int _argIndex = 1;
    if (ids == null) {
      _stmt.bindNull(_argIndex);
    } else {
      for (Long _item : ids) {
        if (_item == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, _item);
        }
        _argIndex++;
      }
    }
    __db.beginTransaction();
    try {
      _stmt.executeUpdateDelete();
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
