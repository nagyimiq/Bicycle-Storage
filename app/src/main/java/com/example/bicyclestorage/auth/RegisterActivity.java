package com.example.bicyclestorage.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bicyclestorage.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;

/**
 * Registration flow:
 *  - Create user (Firebase Auth)
 *  - Create profile document in Firestore (users/{uid})
 *  - On success, sign out and navigate back to LoginActivity (email prefilled)
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private EditText emailField, passwordField, usernameField;
    private Button registerButton;
    private TextView toLogin;
    private ProgressBar progressBar;

    private FirebaseAuth auth;
    private final FirebaseUserRepository repo = new FirebaseUserRepository();

    private boolean submitting = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        setContentView(R.layout.activity_register);

        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        usernameField = findViewById(R.id.usernameField);
        registerButton = findViewById(R.id.registerButton);
        toLogin = findViewById(R.id.toLogin);
        progressBar = null;

        registerButton.setOnClickListener(v -> {
            hideKeyboard();
            startRegistration();
        });

        toLogin.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
    }

    private void startRegistration() {
        if (submitting) return;

        String email = emailField.getText().toString().trim();
        String pass  = passwordField.getText().toString().trim();
        String uname = usernameField.getText().toString().trim();

        if (!validate(email, pass, uname)) return;

        submitting = true;
        setUiEnabled(false);
        Toast.makeText(this, "Registration in progress...", Toast.LENGTH_SHORT).show();

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> {
                    if (res.getUser() == null) {
                        fail("Unknown auth error (user null)");
                        return;
                    }
                    String uid = res.getUser().getUid();
                    Log.d(TAG, "Auth success uid=" + uid);

                    // Create profile – only after SUCCESS redirect + signOut
                    repo.createUserProfile(uid, email, uname)
                            .addOnSuccessListener(unused -> {
                                Log.d(TAG, "Profile saved: " + uid);
                                doRedirectAfterProfile(email);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Profile save error: " + e.getMessage(), e);
                                fail("Profile save error: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Auth error: " + e.getMessage(), e);
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        fail("This email is already registered. Please sign in!");
                    } else {
                        fail("Registration error: " + e.getMessage());
                    }
                });
    }

    private void doRedirectAfterProfile(String email) {
        // Sign out so LoginActivity does not auto-forward
        if (auth.getCurrentUser() != null) {
            auth.signOut();
        }

        Toast.makeText(this, "Registration successful! Please sign in.", Toast.LENGTH_LONG).show();

        Intent i = new Intent(this, LoginActivity.class);
        i.putExtra("prefill_email", email);
        i.putExtra("reg_success", true);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private boolean validate(String email, String pass, String uname) {
        if (uname.isEmpty()) {
            usernameField.setError("Required");
            usernameField.requestFocus();
            return false;
        }
        if (email.isEmpty()) {
            emailField.setError("Required");
            emailField.requestFocus();
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailField.setError("Invalid email");
            emailField.requestFocus();
            return false;
        }
        if (pass.isEmpty()) {
            passwordField.setError("Required");
            passwordField.requestFocus();
            return false;
        }
        if (pass.length() < 6) {
            passwordField.setError("At least 6 characters");
            passwordField.requestFocus();
            return false;
        }
        return true;
    }

    private void fail(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        submitting = false;
        setUiEnabled(true);
    }

    private void setUiEnabled(boolean enabled) {
        emailField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        usernameField.setEnabled(enabled);
        registerButton.setEnabled(enabled);
        toLogin.setEnabled(enabled);
        if (progressBar != null) {
            progressBar.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
        registerButton.setAlpha(enabled ? 1f : 0.6f);
    }

    private void hideKeyboard() {
        View v = getCurrentFocus();
        if (v != null) {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }
}