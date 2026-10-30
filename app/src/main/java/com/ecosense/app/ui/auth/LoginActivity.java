package com.ecosense.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ecosense.app.R;
import com.ecosense.app.ui.dashboard.DashboardActivity;
import com.ecosense.app.utils.Prefs;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin, btnSignup, btnReset, btnGuest;
    private ProgressBar progress;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnSignup = findViewById(R.id.btnSignup);
        btnReset = findViewById(R.id.btnReset);
        btnGuest = findViewById(R.id.btnGuest);
        progress = findViewById(R.id.progress);
        auth = FirebaseAuth.getInstance();

        btnLogin.setOnClickListener(v -> {
            if (!validate(true)) return;
            setBusy(true);
            auth.signInWithEmailAndPassword(text(etEmail), text(etPassword))
                    .addOnCompleteListener(this::onAuthResult);
        });

        btnSignup.setOnClickListener(v -> {
            if (!validate(true)) return;
            setBusy(true);
            auth.createUserWithEmailAndPassword(text(etEmail), text(etPassword))
                    .addOnCompleteListener(this::onAuthResult);
        });

        btnReset.setOnClickListener(v -> {
            if (!validate(false)) return;
            setBusy(true);
            auth.sendPasswordResetEmail(text(etEmail)).addOnCompleteListener(t -> {
                setBusy(false);
                Toast.makeText(this, t.isSuccessful() ? "Password reset email sent."
                        : "Could not send reset email.", Toast.LENGTH_LONG).show();
            });
        });

        btnGuest.setOnClickListener(v -> {
            Prefs.get(this).setGuest(true);
            goToDashboard();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (auth.getCurrentUser() != null) goToDashboard();
    }

    private void onAuthResult(Task<AuthResult> task) {
        setBusy(false);
        if (task.isSuccessful()) {
            Prefs.get(this).setGuest(false);
            goToDashboard();
        } else {
            String m = task.getException() != null ? task.getException().getMessage() : "Authentication failed";
            Toast.makeText(this, m, Toast.LENGTH_LONG).show();
        }
    }

    private boolean validate(boolean needPassword) {
        String email = text(etEmail);
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email");
            return false;
        }
        if (needPassword && text(etPassword).length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            return false;
        }
        return true;
    }

    private static String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!busy);
        btnSignup.setEnabled(!busy);
        btnReset.setEnabled(!busy);
        btnGuest.setEnabled(!busy);
    }

    private void goToDashboard() {
        startActivity(new Intent(this, DashboardActivity.class)); // explicit intent
        finish();
    }
}
