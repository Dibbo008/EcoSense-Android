package com.ecosense.app.data.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.ecosense.app.data.local.AppDatabase;
import com.ecosense.app.data.local.EnvLog;
import com.ecosense.app.data.local.EnvLogDao;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Firestore sync: users/{uid}/logs/{timestamp}. Callbacks are delivered on the main thread. */
public final class FirebaseHelper {

    public interface Callback {
        void onResult(boolean ok, String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int BATCH_LIMIT = 400; // Firestore max is 500 writes per batch

    private FirebaseHelper() {}

    public static boolean isSignedIn() {
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    private static void post(Callback cb, boolean ok, String msg) {
        if (cb != null) MAIN.post(() -> cb.onResult(ok, msg));
    }

    private static Map<String, Object> toMap(EnvLog l) {
        Map<String, Object> m = new HashMap<>();
        m.put("timestamp", l.timestamp);
        m.put("temperature", l.temperature);
        m.put("humidity", l.humidity);
        m.put("pm25", l.pm25);
        m.put("pm10", l.pm10);
        m.put("aqi", l.aqi);
        m.put("source", l.source);
        return m;
    }

    private static double num(DocumentSnapshot d, String key) {
        Double v = d.getDouble(key);
        return v != null ? v : 0;
    }

    /** Uploads readings that have not been synced yet. */
    public static void uploadUnsynced(Context context, Callback cb) {
        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            post(cb, false, "Sign in to sync with the cloud.");
            return;
        }
        final Context app = context.getApplicationContext();
        AppDatabase.IO.execute(() -> {
            try {
                EnvLogDao dao = AppDatabase.getInstance(app).envLogDao();
                List<EnvLog> pending = dao.getUnsynced();
                if (pending.isEmpty()) {
                    post(cb, true, "Everything is already synced.");
                    return;
                }
                if (pending.size() > BATCH_LIMIT) {
                    pending = new ArrayList<>(pending.subList(0, BATCH_LIMIT));
                }
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                CollectionReference col = db.collection("users").document(user.getUid()).collection("logs");
                WriteBatch batch = db.batch();
                final List<Long> ids = new ArrayList<>();
                for (EnvLog l : pending) {
                    batch.set(col.document(String.valueOf(l.timestamp)), toMap(l));
                    ids.add(l.id);
                }
                batch.commit()
                        .addOnSuccessListener(v -> AppDatabase.IO.execute(() -> {
                            dao.markSynced(ids);
                            post(cb, true, "Synced " + ids.size() + " readings.");
                        }))
                        .addOnFailureListener(e -> post(cb, false, "Sync failed: " + e.getMessage()));
            } catch (Exception e) {
                post(cb, false, "Sync failed: " + e.getMessage());
            }
        });
    }

    /** Downloads cloud readings that are missing locally. */
    public static void downloadAll(Context context, Callback cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            post(cb, false, "Sign in to restore from the cloud.");
            return;
        }
        final Context app = context.getApplicationContext();
        try {
            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .collection("logs").get()
                    .addOnSuccessListener(snap -> AppDatabase.IO.execute(() -> {
                        EnvLogDao dao = AppDatabase.getInstance(app).envLogDao();
                        int added = 0;
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            Long ts = d.getLong("timestamp");
                            if (ts == null || dao.countByTimestamp(ts) > 0) continue;
                            EnvLog l = new EnvLog();
                            l.timestamp = ts;
                            l.temperature = num(d, "temperature");
                            l.humidity = num(d, "humidity");
                            l.pm25 = num(d, "pm25");
                            l.pm10 = num(d, "pm10");
                            l.aqi = (int) num(d, "aqi");
                            l.synced = true;
                            String src = d.getString("source");
                            if (src != null) l.source = src;
                            dao.insert(l);
                            added++;
                        }
                        post(cb, true, "Restored " + added + " readings.");
                    }))
                    .addOnFailureListener(e -> post(cb, false, "Restore failed: " + e.getMessage()));
        } catch (Exception e) {
            post(cb, false, "Restore failed: " + e.getMessage());
        }
    }
}
