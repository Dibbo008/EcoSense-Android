package com.ecosense.app.data.remote;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat history stored in Firebase Realtime Database at users/{uid}/chat/{pushId}.
 * Falls back to an in-memory chat (this app session only) when the user is a guest or the
 * database is not configured, so the screen always works.
 */
public final class ChatRepository {

    public static final class Message {
        public String id;
        public boolean fromUser;
        public String text;
        public long ts;
    }

    public interface Listener {
        void onAdded(Message m);
        void onRemoved(String id);
        void onCleared();
        void onError(String message);
    }

    private static final List<Message> LOCAL = new ArrayList<>();
    private static long localCounter = 0;

    private final Listener listener;
    private final DatabaseReference ref; // null = local mode
    private final Handler main = new Handler(Looper.getMainLooper());
    private ChildEventListener childListener;

    public ChatRepository(Listener listener) {
        this.listener = listener;
        this.ref = resolveRef();
    }

    @Nullable
    private static DatabaseReference resolveRef() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) return null;
            return FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("chat");
        } catch (Exception e) {
            return null; // e.g. Realtime Database URL missing in google-services.json
        }
    }

    public boolean isCloud() {
        return ref != null;
    }

    /** Starts delivering existing and new messages to the listener (main thread). */
    public void start() {
        if (ref == null) {
            for (Message m : new ArrayList<>(LOCAL)) listener.onAdded(m);
            return;
        }
        childListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snap, @Nullable String prev) {
                Message m = parse(snap);
                if (m != null) listener.onAdded(m);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snap, @Nullable String prev) { }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snap) {
                if (snap.getKey() != null) listener.onRemoved(snap.getKey());
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snap, @Nullable String prev) { }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.getMessage());
            }
        };
        ref.limitToLast(100).addChildEventListener(childListener);
    }

    public void stop() {
        if (ref != null && childListener != null) {
            ref.removeEventListener(childListener);
            childListener = null;
        }
    }

    public void send(boolean fromUser, String text) {
        long ts = System.currentTimeMillis();
        if (ref == null) {
            final Message m = new Message();
            m.id = "local" + (localCounter++);
            m.fromUser = fromUser;
            m.text = text;
            m.ts = ts;
            LOCAL.add(m);
            main.post(() -> listener.onAdded(m));
            return;
        }
        Map<String, Object> value = new HashMap<>();
        value.put("role", fromUser ? "user" : "bot");
        value.put("text", text);
        value.put("ts", ts);
        ref.push().setValue(value).addOnFailureListener(e ->
                listener.onError("Could not save to the cloud: " + e.getMessage()));
    }

    public void clear() {
        if (ref == null) {
            LOCAL.clear();
            main.post(listener::onCleared);
        } else {
            ref.removeValue().addOnFailureListener(e ->
                    listener.onError("Could not clear the cloud chat: " + e.getMessage()));
        }
    }

    @Nullable
    private static Message parse(DataSnapshot snap) {
        String role = snap.child("role").getValue(String.class);
        String text = snap.child("text").getValue(String.class);
        Long ts = snap.child("ts").getValue(Long.class);
        if (role == null || text == null || snap.getKey() == null) return null;
        Message m = new Message();
        m.id = snap.getKey();
        m.fromUser = "user".equals(role);
        m.text = text;
        m.ts = ts != null ? ts : 0L;
        return m;
    }
}
