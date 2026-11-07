package com.ecosense.app.data.local;

import android.database.Cursor;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface EnvLogDao {
    @Insert
    long insert(EnvLog log);

    @Query("SELECT * FROM env_logs ORDER BY timestamp DESC")
    LiveData<List<EnvLog>> observeAll();

    @Query("SELECT * FROM env_logs ORDER BY timestamp DESC")
    Cursor getAllCursor();

    @Query("SELECT * FROM env_logs ORDER BY timestamp DESC LIMIT 1")
    EnvLog getLatest();

    @Query("SELECT * FROM env_logs ORDER BY timestamp DESC LIMIT :n")
    List<EnvLog> getRecent(int n);

    @Query("SELECT * FROM env_logs WHERE timestamp >= :from ORDER BY timestamp ASC")
    List<EnvLog> since(long from);

    @Query("SELECT * FROM env_logs WHERE synced = 0")
    List<EnvLog> getUnsynced();

    @Query("UPDATE env_logs SET synced = 1 WHERE id IN (:ids)")
    void markSynced(List<Long> ids);

    @Query("SELECT COUNT(*) FROM env_logs WHERE timestamp = :ts")
    int countByTimestamp(long ts);

    @Query("DELETE FROM env_logs")
    void clearAll();
}
