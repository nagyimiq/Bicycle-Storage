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
 * Regisztráció:
 *  - Felhasználó létrehozása (Firebase Auth)
 *  - Profil dokumentum létrehozása Firestore-ban (users/{uid})
 *  - Siker esetén kijelentkezés + vissza LoginActivity-re (email előtöltve)
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
        Toast.makeText(this, "Regisztráció folyamatban...", Toast.LENGTH_SHORT).show();

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> {
                    if (res.getUser() == null) {
                        fail("Ismeretlen auth hiba (user null)");
                        return;
                    }
                    String uid = res.getUser().getUid();
                    Log.d(TAG, "Auth success uid=" + uid);

                    // PROFIL létrehozás – csak SIKER után redirect + signOut
                    repo.createUserProfile(uid, email, uname)
                            .addOnSuccessListener(unused -> {
                                Log.d(TAG, "Profil mentve: " + uid);
                                doRedirectAfterProfile(email);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Profil mentési hiba: " + e.getMessage(), e);
                                fail("Profil mentési hiba: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Auth hiba: " + e.getMessage(), e);
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        fail("Ez az email már regisztrálva van. Jelentkezz be!");
                    } else {
                        fail("Regisztráció hiba: " + e.getMessage());
                    }
                });
    }

    private void doRedirectAfterProfile(String email) {
        // Kijelentkeztetjük, hogy a LoginActivity ne lépjen tovább automatikusan
        if (auth.getCurrentUser() != null) {
            auth.signOut();
        }

        Toast.makeText(this, "Sikeres regisztráció! Jelentkezz be.", Toast.LENGTH_LONG).show();

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
            usernameField.setError("Kötelező");
            usernameField.requestFocus();
            return false;
        }
        if (email.isEmpty()) {
            emailField.setError("Kötelező");
            emailField.requestFocus();
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailField.setError("Érvénytelen email");
            emailField.requestFocus();
            return false;
        }
        if (pass.isEmpty()) {
            passwordField.setError("Kötelező");
            passwordField.requestFocus();
            return false;
        }
        if (pass.length() < 6) {
            passwordField.setError("Legalább 6 karakter");
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