package com.ecosense.app.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.annotation.NonNull;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {EnvLog.class, SymptomEntry.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    /** Shared background executor for all database / disk work. */
    public static final ExecutorService IO = Executors.newFixedThreadPool(2);

    private static volatile AppDatabase instance;

    public abstract EnvLogDao envLogDao();

    public abstract SymptomDao symptomDao();

    /** v1 -> v2: adds the symptom diary table without touching existing readings. */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `symptom_entries` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`timestamp` INTEGER NOT NULL, "
                    + "`type` INTEGER NOT NULL, "
                    + "`severity` INTEGER NOT NULL, "
                    + "`note` TEXT NOT NULL, "
                    + "`aqi` INTEGER NOT NULL, "
                    + "`pm25` REAL NOT NULL, "
                    + "`temperature` REAL NOT NULL, "
                    + "`humidity` REAL NOT NULL)");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "ecosense.db")
                            .addMigrations(MIGRATION_1_2)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
