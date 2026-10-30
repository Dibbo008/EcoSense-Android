package com.ecosense.app.data.local;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Read-only Content Provider exposing the log table.
 * content://com.ecosense.app.provider/logs
 * Never call it from the main thread (Room restriction).
 */
public class EnvLogProvider extends ContentProvider {
    public static final String AUTHORITY = "com.ecosense.app.provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/logs");
    private static final int LOGS = 1;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, "logs", LOGS);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (MATCHER.match(uri) != LOGS) {
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        Context context = getContext();
        if (context == null) return null;
        return AppDatabase.getInstance(context).envLogDao().getAllCursor();
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return MATCHER.match(uri) == LOGS
                ? "vnd.android.cursor.dir/vnd.com.ecosense.app.log" : null;
    }

    // Writes are intentionally unsupported: the provider is a read-only sharing endpoint.
    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }
}
