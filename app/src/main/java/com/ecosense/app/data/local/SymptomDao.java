package com.ecosense.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SymptomDao {
    @Insert
    long insert(SymptomEntry entry);

    @Query("SELECT * FROM symptom_entries ORDER BY timestamp DESC")
    LiveData<List<SymptomEntry>> observeAll();

    @Query("SELECT * FROM symptom_entries ORDER BY timestamp ASC")
    List<SymptomEntry> getAllAsc();

    @Query("SELECT * FROM symptom_entries ORDER BY timestamp DESC")
    List<SymptomEntry> getAllDesc();

    @Query("DELETE FROM symptom_entries WHERE id = :id")
    void deleteById(long id);
}
