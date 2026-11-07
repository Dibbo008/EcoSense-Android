package com.ecosense.app.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.ecosense.app.data.local.EnvLogProvider;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Exports all logs (read through our own ContentProvider) to a CSV in Downloads. */
public final class CsvExporter {

    public static final class Result {
        public final String displayPath;
        public final Uri uri; // null on Android 9 and below (plain file)
        Result(String displayPath, Uri uri) {
            this.displayPath = displayPath;
            this.uri = uri;
        }
    }

    private CsvExporter() {}

    /** Blocking. Call from a background thread. */
    public static Result export(Context context) throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "ecosense_logs_" + stamp + ".csv";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Could not create file in Downloads");
            try (OutputStream os = resolver.openOutputStream(uri)) {
                if (os == null) throw new IOException("Could not open output stream");
                writeCsv(context, os);
            }
            return new Result("Downloads/" + fileName, uri);
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create Downloads folder");
            File file = new File(dir, fileName);
            try (OutputStream os = new FileOutputStream(file)) {
                writeCsv(context, os);
            }
            return new Result(file.getAbsolutePath(), null);
        }
    }

    /** Saves any text file (e.g. the symptom report) into Downloads. Blocking. */
    public static Result saveText(Context context, String fileName, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Could not create file in Downloads");
            try (OutputStream os = resolver.openOutputStream(uri)) {
                if (os == null) throw new IOException("Could not open output stream");
                os.write(bytes);
            }
            return new Result("Downloads/" + fileName, uri);
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create Downloads folder");
        File file = new File(dir, fileName);
        try (OutputStream os = new FileOutputStream(file)) {
            os.write(bytes);
        }
        return new Result(file.getAbsolutePath(), null);
    }

    private static void writeCsv(Context context, OutputStream os) throws IOException {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
             Cursor c = context.getContentResolver().query(EnvLogProvider.CONTENT_URI, null, null, null, null)) {
            w.write("id,timestamp,date,temperature_c,humidity_pct,pm25,pm10,us_aqi,synced,source\n");
            if (c == null) return;
            int iId = c.getColumnIndexOrThrow("id");
            int iTs = c.getColumnIndexOrThrow("timestamp");
            int iT = c.getColumnIndexOrThrow("temperature");
            int iH = c.getColumnIndexOrThrow("humidity");
            int iP25 = c.getColumnIndexOrThrow("pm25");
            int iP10 = c.getColumnIndexOrThrow("pm10");
            int iAqi = c.getColumnIndexOrThrow("aqi");
            int iSync = c.getColumnIndexOrThrow("synced");
            int iSrc = c.getColumnIndexOrThrow("source");
            while (c.moveToNext()) {
                long ts = c.getLong(iTs);
                w.write(String.format(Locale.US, "%d,%d,%s,%.2f,%.2f,%.2f,%.2f,%d,%d,\"%s\"\n",
                        c.getLong(iId), ts, iso.format(new Date(ts)),
                        c.getDouble(iT), c.getDouble(iH), c.getDouble(iP25), c.getDouble(iP10),
                        c.getInt(iAqi), c.getInt(iSync),
                        c.getString(iSrc).replace("\"", "\"\"")));
            }
        }
    }
}
