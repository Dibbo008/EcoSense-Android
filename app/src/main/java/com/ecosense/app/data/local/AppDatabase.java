package com.ecosense.app.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {EnvLog.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    /** Shared background executor for all database / disk work. */
    public static final ExecutorService IO = Executors.newFixedThreadPool(2);

    private static volatile AppDatabase instance;

    public abstract EnvLogDao envLogDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "ecosense.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
